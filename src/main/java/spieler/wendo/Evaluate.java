package spieler.wendo;

class Evaluate {

  /**
   * Positional square weights, indexed row*SIZE+col: corners high, X-/C-squares strongly negative.
   */
  private static final int[] WEIGHTS = {
      120, -20, 20, 5, 5, 20, -20, 120,
      -20, -40, -5, -5, -5, -5, -40, -20,
      20, -5, 15, 3, 3, 15, -5, 20,
      5, -5, 3, 3, 3, 3, -5, 5,
      5, -5, 3, 3, 3, 3, -5, 5,
      20, -5, 15, 3, 3, 15, -5, 20,
      -20, -40, -5, -5, -5, -5, -40, -20,
      120, -20, 20, 5, 5, 20, -20, 120
  };

  private static final int ENDGAME_THRESHOLD = 50;
  private static final int ENDGAME_DISC_WEIGHT = 100;
  private static final int ENDGAME_CORNER_WEIGHT = 1000;
  private static final int CORNER_WEIGHT = 25;
  private static final int MOBILITY_WEIGHT = 5;
  private static final int[][] CORNERS = {{0, 0}, {0, 7}, {7, 0}, {7, 7}};
  private static final int SIZE = 8;

  /**
   * Antisymmetric heuristic: evaluate(b, side) == -evaluate(b, opponent(side)).
   * Positive = good for {@code side}.
   */
  int evaluate(Board b, int side) {
    int opp = b.opponent(side);
    int myCount = b.count(side);
    int oppCount = b.count(opp);
    int discs = myCount + oppCount;

    // endgame: counting
    if (discs >= ENDGAME_THRESHOLD) {
      int discDiff = myCount - oppCount;
      return ENDGAME_DISC_WEIGHT * discDiff
          + ENDGAME_CORNER_WEIGHT * cornerScore(b, side, opp);
    }

    // early/midgame: position + mobility + corners; raw disc count ignored
    return CORNER_WEIGHT * cornerScore(b, side, opp)
        + MOBILITY_WEIGHT * mobilityScore(b, side, opp)
        + positionalScore(b, side, opp);
  }

  /**
   * Count corners owned by {@code side} minus corners owned by {@code opp}.
   * {0,0},{0,7},{7,0},{7,7}
   */
  int cornerScore(Board b, int side, int opp) {
    int score = 0;
    for (int[] c : CORNERS) {
      int cell = b.getBoardCell(c[0], c[1]);
      if (cell == side) {
        score++;
      } else if (cell == opp) {
        score--;
      }
    }
    return score;
  }

  int mobilityScore(Board b, int side, int opp) {
    int myMoves = b.legalMoves(side).size();
    int oppMoves = b.legalMoves(opp).size();
    int total = myMoves + oppMoves;
    if (total == 0) {
      return 0;
    }
    return 100 * (myMoves - oppMoves) / total;
  }

  int positionalScore(Board b, int side, int opp) {
    int score = 0;
    for (int row = 0; row < SIZE; row++) {
      for (int col = 0; col < SIZE; col++) {
        int cell = b.getBoardCell(row, col);
        int w = WEIGHTS[row * SIZE + col];
        if (cell == side) {
          score += w;
        } else if (cell == opp) {
          score -= w;
        }
      }
    }
    return score;
  }

  /**
   * Static desirability of a single square, taken from the positional weight table (corners high,
   * X-/C-squares negative). Used by the search for move ordering. It is a guess at how good a move
   * to that square is likely to be.
   */
  int squareValue(int row, int col) {
    return WEIGHTS[row * SIZE + col];
  }
}
