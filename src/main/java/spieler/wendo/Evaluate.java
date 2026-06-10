package spieler.wendo;

public class Evaluate {

  /**
   * Evaluates the given board position from the perspective of the given player.
   *
   * <p>Positive values are good for the player, negative values are bad. The exact scale is
   * arbitrary, but it should be consistent across all positions so that the search can compare
   * them.</p>
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

  /**
   * Antisymmetric heuristic: evaluate(b, side) == -evaluate(b, opponent(side)).
   * Positive = good for {@code side}.
   */
  int evaluate(Board b, int side) {
    int opp = b.opponent(side);
    int discs = b.count(side) + b.count(opp);

    int corner = cornerScore(b, side, opp);
    int mobility = mobilityScore(b, side, opp);
    int positional = positionalScore(b, side, opp);
    int discDiff = b.discDiff(side);

    if (discs >= 50) {                 // endgame: counting wins
      return 100 * discDiff + 1000 * corner;
    }
    // midgame: ignore raw discs, weight position + mobility + corners
    return 25 * corner + 5 * mobility + positional;
  }

  // each helper returns (side's value) - (opp's value)  -> antisymmetric
  int cornerScore(Board b, int side, int opp) {
    int[][] corners = {{0, 0}, {0, 7}, {7, 0}, {7, 7}};
    int score = 0;
    for (int[] c : corners) {
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
    for (int row = 0; row < 8; row++) {
      for (int col = 0; col < 8; col++) {
        int cell = b.getBoardCell(row, col);
        int w = WEIGHTS[row * 8 + col];
         if( cell == side) {
           score += w;
         }  else if (cell == opp) {
           score -= w;
         }
      }
    }
    return score;
  }
}
