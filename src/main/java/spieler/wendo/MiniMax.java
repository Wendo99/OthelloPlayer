package spieler.wendo;

import java.util.List;
import java.util.Optional;

/**
 * NegaScout search (an alpha-beta / MiniMax variant) with iterative deepening under a time budget.
 *
 * <p><strong>Not thread-safe.</strong> A single instance keeps mutable per-search state
 * ({@code deadlineNanos}, {@code nodeCount}); it must run one search at a time.</p>
 */
class MiniMax {

    /**
     * Sentinel scores, larger in magnitude than any real evaluation (effectively +/- infinity).
     */
    private static final int WIN = 1_000_000;
    private static final int LOSS = -1_000_000;

    /**
     * Read the clock once every {@code TIME_CHECK_MASK + 1} nodes (power-of-two minus one).
     */
    private static final int TIME_CHECK_MASK = 1023;

    /**
     * Board width; a move (row, col) is encoded as {@code row * BOARD_SIZE + col}.
     */
    private static final int BOARD_SIZE = 8;

    /**
     * Size of the killer table; the search depth never exceeds the board's empty cells (&le; 60).
     */
    private static final int MAX_PLY = 64;

    /**
     * Per depth, up to two moves (encoded row*8+col) that recently caused a beta cutoff; -1 = empty.
     */
    private final int[][] killers = new int[MAX_PLY][2];

    private final Evaluate evaluator;

    /**
     * Monotonic cutoff (from {@link System#nanoTime()}, in nanoseconds) for the current search;
     * {@link Long#MAX_VALUE} disables it.
     */
    private long deadlineNanos = Long.MAX_VALUE;

    /**
     * Free-running node counter; the time check fires only when its low bits hit zero.
     */
    private long nodeCount;

    /**
     * Thrown to unwind the recursion when the time budget is exhausted. Stack trace is suppressed
     * because it is used purely as control flow, thrown at most once per search.
     */
    private static final class TimeUpException extends RuntimeException {

        private TimeUpException() {
            super(null, null, false, false);
        }
    }

    private static final TimeUpException TIME_UP = new TimeUpException();

    MiniMax(Evaluate evaluator) {
        this.evaluator = evaluator;
        clearKillers();
    }

    /**
     * This is the core search method. Given a board and how many plies to look ahead, it returns a
     * score from the side-to-move's perspective. It does four things in order
     *
     * <p>1. Stop conditions: if depth ran out or the game is over → evaluate the board and
     * return.</p>
     * <p>2. Pass handling: if the side has no legal moves but the game isn't over → make a
     * pass move and recurse with the other side.</p>
     * <p>3. Try every legal move: for each move, copy the board, play the move, recurse
     * to score the result, keep the best.</p>
     * <p>4. The NegaScout twist: first move gets a full search; later moves get a cheap "is
     * this even better than what I have?" probe, and only get a full re-search if the probe says
     * yes.</p>
     *
     * @param board the current board state
     * @param depth how many plies to look ahead (decrements on each recursive call)
     * @param alpha the best score that the side to move can guarantee so far
     * @param beta  the best score that the opponent can guarantee so far
     * @param side  the side to move (BLACK or WHITE)
     * @return score of {@code board} from the perspective of {@code side} to move.
     */
    private int negascout(Board board, int depth, int alpha, int beta, int side) {
        // 0. Time guard
        if ((++nodeCount & TIME_CHECK_MASK) == 0 && System.nanoTime() > deadlineNanos) {
            throw TIME_UP;
        }

        if (depth == 0) {
            return evaluator.evaluate(board, side);
        }

        int oppColor = board.opponent(side);
        List<int[]> moves = board.legalMoves(side);

        if (moves.isEmpty()) {
            if (board.legalMoves(oppColor).isEmpty()) {
                return evaluator.evaluate(board, side);
            }
            return handlePass(board, depth, alpha, beta, side, oppColor);
        }

        orderMoves(moves, depth);

        boolean first = true;
        int best = LOSS;

        // 3. Try every legal move
        for (int[] mv : moves) {
            Board child = board.copy();
            child.applyMove(mv[0], mv[1], side);

            // 4. The NegaScout twist
            int score = searchChild(child, depth, alpha, beta, oppColor, first);
            first = false;

            if (score > best) {
                best = score;
            }
            if (best > alpha) {
                alpha = best;
            }
            if (alpha >= beta) {
                recordKiller(depth, mv);
                break;
            }
        }
        return best;
    }

    /**
     * Sorts {@code moves} in place, best guess first: descending by the static square value from the
     * evaluator's weight table. A pure heuristic ordering — it does not change which move is correct,
     * only how quickly NegaScout finds and proves it, which is what lets the search reach more plies.
     */
    private void orderMoves(List<int[]> moves) {
        moves.sort((a, b) -> Integer.compare(
                evaluator.squareValue(b[0], b[1]),
                evaluator.squareValue(a[0], a[1])));
    }

    /**
     * Applies the static ordering ({@link #orderMoves(List)}) and then promotes this level's killer
     * moves to the front.
     *
     * @param moves the legal moves to order, in place
     * @param depth the current search depth, indexing this level's killer table
     */
    private void orderMoves(List<int[]> moves, int depth) {
        assert depth >= 0 && depth < MAX_PLY : "depth out of killer-table range: " + depth;
        orderMoves(moves);
        promoteKiller(moves, killers[depth][1]);
        promoteKiller(moves, killers[depth][0]);
    }

    /**
     * Moves the killer square {@code sq} (encoded {@code row*BOARD_SIZE+col}) to the front of
     * {@code moves}, if present. A negative {@code sq} means "no killer" and is ignored.
     */
    private void promoteKiller(List<int[]> moves, int sq) {
        if (sq < 0) {
            return;                              // empty slot
        }
        for (int i = 0; i < moves.size(); i++) {
            int[] mv = moves.get(i);
            if (encode(mv) == sq) {              // value match
                moves.remove(i);
                moves.addFirst(mv);
                return;
            }
        }
    }

    /**
     * Scores one child with NegaScout: a full window for the principal-variation (first) move, and a
     * null-window scout — re-searched on a fail-high — for the rest.
     */
    private int searchChild(Board child, int depth, int alpha, int beta, int opp, boolean first) {
        if (first) {
            // Principal variation: full window.
            return -negascout(child, depth - 1, -beta, -alpha, opp);
        }
        // Scout: null window [-alpha-1, -alpha].
        int score = -negascout(child, depth - 1, -alpha - 1, -alpha, opp);
        if (alpha < score && score < beta) {
            // Failed high -> re-search with full window.
            score = -negascout(child, depth - 1, -beta, -score, opp);
        }
        return score;
    }

    /**
     * Handles a forced pass. Passing is not a real move, so it does not cost a ply; it only swaps the
     * side to move. The search therefore recurses at the same depth with the opposite side.
     *
     * @param board the current board state
     * @param depth how many plies to look ahead
     * @param alpha the best score the side to move can guarantee so far
     * @param beta  the best score the opponent can guarantee so far
     * @param side  the side to move (BLACK or WHITE)
     * @param opp   the opponent's color
     * @return the score after {@code side} passes, from {@code side}'s perspective
     */
    private int handlePass(Board board, int depth, int alpha, int beta, int side, int opp) {
        Board passed = board.copy();
        passed.applyMove(0, Board.PASS, side);
        return -negascout(passed, depth, -beta, -alpha, opp);
    }

    /**
     * Fixed-depth entry point: searches exactly {@code depth} plies with no time limit. Returns the
     * best move as an {@code int[]{row, col}}, or an empty Optional if there is no legal move (caller
     * must pass).
     *
     * <p><strong>Test-only.</strong> Live play uses the time-bounded
     * {@link #bestMove(Board, int, int, long)}; this overload exists so tests can assert a
     * deterministic fixed-depth result. Do not wire it into {@code Spieler}.</p>
     *
     * @param board the current board state
     * @param depth how many plies to look ahead
     * @param side  the side to move (BLACK or WHITE)
     * @return the best move as (row, col) pair, or an empty Optional if no legal move exists
     */
    Optional<int[]> bestMove(Board board, int depth, int side) {
        deadlineNanos = Long.MAX_VALUE;
        nodeCount = 0;
        clearKillers();

        List<int[]> moves = board.legalMoves(side);
        if (moves.isEmpty()) {
            return Optional.empty();
        }
        orderMoves(moves);
        return Optional.of(searchRoot(board, depth, side, moves));
    }

    /**
     * Time-bounded iterative deepening: searches depth 1, 2, 3, … up to {@code maxDepth}, and returns
     * the best move from the deepest search that fully completed before {@code timeBudgetMs} elapsed.
     * Each completed depth promotes its best move to the front of the move list, so the next (deeper)
     * pass searches the principal variation first — sharpening the pruning as the player goes.
     *
     * @param board        the current board state
     * @param maxDepth     hard ceiling on plies; expected >= 1 (a value <= 0 skips the search and
     *                     returns the best statically-ordered legal move)
     * @param side         the side to move (BLACK or WHITE)
     * @param timeBudgetMs how long this move may take, in milliseconds (>= 0)
     * @return the best move as (row, col) pair, or an empty Optional if no legal move exists
     */
    Optional<int[]> bestMove(Board board, int maxDepth, int side, long timeBudgetMs) {
        deadlineNanos = System.nanoTime() + timeBudgetMs * 1_000_000L;
        nodeCount = 0;
        clearKillers();

        List<int[]> moves = board.legalMoves(side);
        if (moves.isEmpty()) {
            return Optional.empty();
        }
        orderMoves(moves);

        int[] best = moves.getFirst(); // fallback: a legal move even if depth 1 never finishes
        for (int depth = 1; depth <= maxDepth; depth++) {
            try {
                best = searchRoot(board, depth, side, moves);
                moveToFront(moves, best); // PV-first ordering for the next, deeper pass
            } catch (TimeUpException ignored) {
                break; // ran out of time mid-depth
            }
        }
        return Optional.of(best);
    }

    /**
     * Searches one full depth over the given pre-ordered root moves and returns the highest-scoring
     * move (the argmax). Unlike {@link #negascout}, which returns a score, this returns the move.
     *
     * @param board the current board state (root position)
     * @param depth how many plies to look ahead
     * @param side  the side to move (BLACK or WHITE)
     * @param moves the root's legal moves, already ordered best-guess first (must be non-empty)
     * @return the best move as an {@code int[]{row, col}}
     */
    @SuppressWarnings("UnnecessaryLocalVariable")
    private int[] searchRoot(Board board, int depth, int side, List<int[]> moves) {
        int[] best = null;
        int alpha = LOSS;
        int beta = WIN;
        boolean first = true;
        int oppColor = board.opponent(side);

        for (int[] mv : moves) {
            Board child = board.copy();
            child.applyMove(mv[0], mv[1], side);
            int score = searchChild(child, depth, alpha, beta, oppColor, first);
            first = false;

            if (best == null || score > alpha) {
                alpha = score;
                best = mv;
            }
        }
        return best;
    }

    /**
     * Moves {@code mv} to the head of {@code moves} (used to search the previous best move first).
     */
    private void moveToFront(List<int[]> moves, int[] mv) {
        if (moves.remove(mv)) {
            moves.addFirst(mv);
        }
    }

    /**
     * Resets the killer table to "empty" ({@code -1}); called at the start of each search.
     */
    private void clearKillers() {
        for (int[] slot : killers) {
            slot[0] = -1;
            slot[1] = -1;
        }
    }

    /**
     * Records {@code mv} as a killer at {@code depth} (a move that just caused a beta cutoff) so it
     * is
     * tried first at sibling nodes. Keeps the two most recent distinct killers per depth.
     */
    private void recordKiller(int depth, int[] mv) {
        assert depth >= 0 && depth < MAX_PLY : "depth out of killer-table range: " + depth;
        int sq = encode(mv);
        if (killers[depth][0] == sq) {
            return;                              // already the primary killer, nothing to do
        }
        killers[depth][1] = killers[depth][0]; // demote old primary to secondary
        killers[depth][0] = sq;                // new primary
    }

    /**
     * Encodes a move as a single int {@code row * BOARD_SIZE + col} for compact killer storage.
     */
    private static int encode(int[] mv) {
        return mv[0] * BOARD_SIZE + mv[1];
    }

    /**
     * Nodes visited by the most recent search — used by the benchmark to compare speed edits.
     */
    long getNodeCount() {
        return nodeCount;
    }
}
