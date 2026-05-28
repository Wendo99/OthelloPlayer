package spieler.wendo;

import java.util.List;

// TODO: Write Tests
public class MiniMax {

  private static final int WIN = 1_000_000;   // sentinel scores
  private static final int LOSS = -1_000_000;


  /**
   * Just disc difference. It's a stand-in until a real Evaluator
   */
  private int evaluate(Board b, int side) {
    // TODO: inject a real Evaluator
    // MUST be symmetric: evaluate(b, side) == -evaluate(b, opponent(side))
    return b.count(side) - b.count(b.opponent(side));   // weak; replace later
  }

  /**
   * This is the brain. Given a board and how many plies to look ahead, it returns a score from the
   * side-to-move's perspective. It does four things in order
   * <p>
   * 1. Stop conditions: if depth ran out or the game is over → score the board and return.
   * 2. Pass handling: if the side has no legal moves but the game isn't over → make a
   * pass move and recurse with the other side.
   * 3. Try every legal move: for each move, copy the board, play the move, recurse
   * to score the result, keep the best.
   * 4. The NegaScout twist: first move gets a full search; later moves get a cheap "is
   * this even better than what I have?" probe, and only get a full re-search if the probe says
   * yes.
   *
   * @return score of {@code board} from the perspective of {@code side} to move.
   */
  private int negascout(Board board, int depth, int alpha, int beta, int side) {
    // 1. Stop conditions: Terminal / depth limit
    if (depth == 0 || board.isGameOver()) {
      return evaluate(board, side);
    }

    int opp = board.opponent(side);
    List<int[]> moves = board.legalMoves(side);

    // 2. Pass handling: No move available, but game not over -> forced PASS (no depth cost,
    //    swap side). Two passes in a row -> isGameOver() was already true.
    if (moves.isEmpty()) {
      return handlePass(board, depth, alpha, beta, side, opp);
    }

    // TODO: ORDER `moves` here (see notes) — NegaScout lives or dies on this.

    boolean first = true;
    int best = LOSS;

    // 3. Try every legal move
    for (int[] mv : moves) {
      Board child = board.copy();
      child.applyMove(mv[0], mv[1], side);

      // 4. The NegaScout twist (extracted helper to reduce cognitive complexity)
      int score = searchChild(child, depth, alpha, beta, opp, first);
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
   * Extracted helper for child search to simplify negascout and reduce complexity score
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
   *
   */
  private int handlePass(Board board, int depth, int alpha, int beta, int side, int opp) {
    Board passed = board.copy();
    passed.applyMove(0, Board.PASS, side);
    return -negascout(passed, depth, -beta, -alpha, opp);
  }

  // TODO: No one is calling so far. Wire it up end-to-end (the framework).

  /**
   * Entry point the Spieler will call.
   * It walks the legal moves once at the root, scores each one by calling negascout on the
   * resulting position, and returns the {@code int[]{row, col}} of the highest-scoring move (or
   * null if no legal moves → caller must pass).
   */
  int[] bestMove(Board board, int depth, int side) {
    int[] best = null;
    int alpha = LOSS;
    int beta = WIN;
    boolean first = true;

    for (int[] mv : board.legalMoves(side)) {
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
