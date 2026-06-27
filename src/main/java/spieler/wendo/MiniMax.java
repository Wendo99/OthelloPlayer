package spieler.wendo;

import java.util.List;
import java.util.Optional;

/**
 * NegaScout search with iterative deepening and a time budget.
 *
 * <p><strong>Not thread-safe.</strong> A single instance keeps mutable per-search state
 * ({@code deadlineNanos}, {@code nodeCount}); it must run one search at a time. Use a separate
 * instance per thread if you ever parallelise.</p>
 */
class MiniMax {

  private static final int WIN = 1_000_000;   // sentinel scores
  private static final int LOSS = -1_000_000;

  /**
   * Read the clock once every {@code TIME_CHECK_MASK + 1} nodes (power-of-two minus one).
   */
  private static final int TIME_CHECK_MASK = 1023;

  private final Evaluate evaluator;

  /**
   * Monotonic cutoff (from {@link System#nanoTime()}, in nanoseconds) for the current search;
   * {@link Long#MAX_VALUE} disables it.
   */
  private long deadlineNanos = Long.MAX_VALUE;

  /**
   * Free-running node counter; the time check fires only when its low bits hit zero.
   */
  private int nodeCount;

  /**
   * Thrown to unwind the recursion when the time budget is exhausted. Stack trace is suppressed
   * because it is used purely as control flow, thrown at most once per search.
   */
  private static final class TimeUp extends RuntimeException {

    private TimeUp() {
      super(null, null, false, false);
    }
  }

  private static final TimeUp TIME_UP = new TimeUp();

  MiniMax(Evaluate evaluator) {
    this.evaluator = evaluator;
  }

  /**
   * This is the brain. Given a board and how many plies to look ahead, it returns a score from the
   * side-to-move's perspective. It does four things in order
   * <p>
   * 1. Stop conditions: if depth ran out or the game is over → evaluate the board and return.
   * 2. Pass handling: if the side has no legal moves but the game isn't over → make a
   * pass move and recurse with the other side.
   * 3. Try every legal move: for each move, copy the board, play the move, recurse
   * to score the result, keep the best.
   * 4. The NegaScout twist: first move gets a full search; later moves get a cheap "is
   * this even better than what I have?" probe, and only get a full re-search if the probe says
   * yes.
   *
   * @param board the current board state
   * @param depth how many plies to look ahead (decrements on each recursive call)
   * @param alpha the best score that the side to move can guarantee so far
   * @param beta  the best score that the opponent can guarantee so far
   * @param side  the side to move (BLACK or WHITE)
   * @return score of {@code board} from the perspective of {@code side} to move.
   */
  private int negascout(Board board, int depth, int alpha, int beta, int side) {
    // 0. Time guard: abandon this search if the budget is spent. Reading the clock at every node
    //    (leaves included) would dominate the search, so we only check once every
    //    TIME_CHECK_MASK+1 nodes. The iterative-deepening caller catches this and keeps the best
    //    move from the last fully completed depth.
    if ((++nodeCount & TIME_CHECK_MASK) == 0 && System.nanoTime() > deadlineNanos) {
      throw TIME_UP;
    }

    // 1. Stop conditions: Terminal / depth limit
    if (depth == 0 || board.isGameOver()) {
      return evaluator.evaluate(board, side);
    }

    int oppColor = board.opponent(side);
    List<int[]> moves = board.legalMoves(side);

    // 2. Pass handling: No move available, but game not over -> forced PASS (no depth cost,
    //    swap side). Two passes in a row -> isGameOver() was already true.
    if (moves.isEmpty()) {
      return handlePass(board, depth, alpha, beta, side, oppColor);
    }

    // Best-first ordering: try high-value squares (corners, edges) before bad ones
    // (X-/C-squares). NegaScout only prunes well when the likely-best move comes first.
    orderMoves(moves);

    boolean first = true;
    int best = LOSS;

    // 3. Try every legal move
    for (int[] mv : moves) {
      Board child = board.copy();
      child.applyMove(mv[0], mv[1], side);

      // 4. The NegaScout twist (extracted helper to reduce cognitive complexity)
      int score = searchChild(child, depth, alpha, beta, oppColor, first);
      first = false;

      if (score > best) {
        best = score;
      }
      if (best > alpha) {
        alpha = best;
      }
      if (alpha >= beta) {
        break;            // beta cutoff
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
        evaluator.squareValue(b[0], b[1]),   // b before a => descending (high squareValue first)
        evaluator.squareValue(a[0], a[1])));
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
   * This is a separate helper because passing doesn't cost depth (it's not really a move,
   * just a forced skip), but it does swap sides. So we recurse with the same depth but opposite
   * side.
   *
   * @param board the current board state
   * @param depth how many plies to look ahead
   * @param alpha the  best score that the side to move can guarantee so far
   * @param beta  the  best score that the opponent can guarantee so far
   * @param side  the side to move (BLACK or WHITE)
   * @param opp   the opponent's color
   * @return the score of the position resulting from {@code side} passing, from {@code side}'s perspective.
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
   * pass searches the principal variation first — sharpening the pruning as we go.
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
      } catch (TimeUp ignored) {
        break; // ran out of time mid-depth: discard it, keep the last completed depth's best
      }
    }
    return Optional.of(best);
  }

  /**
   * Searches one ply level over the given pre-ordered root moves and returns the argmax move.
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

      if (best == null || score > alpha) {   // keep argmax
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
}
