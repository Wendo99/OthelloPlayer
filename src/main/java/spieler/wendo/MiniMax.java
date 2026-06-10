package spieler.wendo;

import java.util.List;

public class MiniMax {

  private static final int WIN = 1_000_000;   // sentinel scores
  private static final int LOSS = -1_000_000;
  private final Evaluate evaluator;

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
   * Entry point Spieler will call.
   * It walks the legal moves once at the root, scores each one by calling negascout on the
   * resulting position, and returns the {@code int[]{row, col}} of the highest-scoring move (or
   * null if no legal moves → caller must pass).
   *
   * @param board the current board state
   * @param depth how  many plies to look ahead
   * @param side  the side to move (BLACK or WHITE)
   * @return the best move as (row, col) pair, or null if no
   */
  int[] bestMove(Board board, int depth, int side) {
    int[] best = null;
    int alpha = LOSS;
    int beta = WIN;
    boolean first = true;

    List<int[]> moves = board.legalMoves(side);
    orderMoves(moves);

    for (int[] mv : moves) {
      Board child = board.copy();
      child.applyMove(mv[0], mv[1], side);
      int score;
      if (first) {
        score = -negascout(child, depth - 1, -beta, -alpha, board.opponent(side));
        first = false;
      } else {
        score = -negascout(child, depth - 1, -alpha - 1, -alpha, board.opponent(side));
        if (alpha < score && score < beta) {
          score = -negascout(child, depth - 1, -beta, -score, board.opponent(side));
        }
      }
      if (best == null || score > alpha) {   // keep argmax
        alpha = score;
        best = mv;
      }
    }
    return best;   // null only if no legal move -> caller passes
  }
}
