package spieler.wendo;

import java.util.List;

public class MiniMax {

  private static final int WIN = 1_000_000;   // sentinel scores
  private static final int LOSS = -1_000_000;
  private final Evaluate evaluator;

  /** Absolute wall-clock cutoff (ms) for the current search; {@link Long#MAX_VALUE} disables it. */
  private long deadline = Long.MAX_VALUE;

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

  public MiniMax(Evaluate evaluator) {
    this.evaluator = evaluator;
  }

  /**
   *
   */
  private int evaluateScore(Board b, int side) {
    return evaluator.evaluate(b, side);
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
    // 0. Time guard: abandon this search if the budget is spent. The iterative-deepening
    //    caller catches this and keeps the best move from the last fully completed depth.
    if (System.currentTimeMillis() > deadline) {
      throw TIME_UP;
    }

    // 1. Stop conditions: Terminal / depth limit
    if (depth == 0 || board.isGameOver()) {
      return evaluateScore(board, side);
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
    moves.sort((a, b) -> evaluator.squareValue(b[0], b[1]) - evaluator.squareValue(a[0], a[1]));
  }

  /**
   * Extracted helper to child search in {@code negascout}
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
   * best move as an {@code int[]{row, col}}, or null if there is no legal move (caller must pass).
   *
   * @param board the current board state
   * @param depth how many plies to look ahead
   * @param side  the side to move (BLACK or WHITE)
   * @return the best move as (row, col) pair, or null if no legal move exists
   */
  int[] bestMove(Board board, int depth, int side) {
    deadline = Long.MAX_VALUE; // no time limit on the fixed-depth search

    List<int[]> moves = board.legalMoves(side);
    if (moves.isEmpty()) {
      return null;
    }
    orderMoves(moves);
    return searchRoot(board, depth, side, moves);
  }

  /**
   * Time-bounded iterative deepening: searches depth 1, 2, 3, … up to {@code maxDepth}, and returns
   * the best move from the deepest search that fully completed before {@code timeBudgetMs} elapsed.
   * Each completed depth promotes its best move to the front of the move list, so the next (deeper)
   * pass searches the principal variation first — sharpening the pruning as we go.
   *
   * @param board        the current board state
   * @param maxDepth     hard ceiling on plies (the search usually stops earlier, on time)
   * @param side         the side to move (BLACK or WHITE)
   * @param timeBudgetMs how long this move may take, in milliseconds
   * @return the best move as (row, col) pair, or null if no legal move exists
   */
  int[] bestMove(Board board, int maxDepth, int side, long timeBudgetMs) {
    deadline = System.currentTimeMillis() + timeBudgetMs;

    List<int[]> moves = board.legalMoves(side);
    if (moves.isEmpty()) {
      return null;
    }
    orderMoves(moves);

    int[] best = moves.get(0); // fallback: a legal move even if depth 1 never finishes
    for (int depth = 1; depth <= maxDepth; depth++) {
      try {
        best = searchRoot(board, depth, side, moves);
        moveToFront(moves, best); // PV-first ordering for the next, deeper pass
      } catch (TimeUp e) {
        break; // ran out of time mid-depth: discard it, keep the last completed depth's best
      }
    }
    return best;
  }

  /**
   * Searches one ply level over the given pre-ordered root moves and returns the argmax move.
   */
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
      moves.add(0, mv);
    }
  }
}
