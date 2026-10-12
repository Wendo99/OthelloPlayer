package spieler.wendo;

/**
 * Static position evaluator: turns a board into a single score from a given side's perspective
 * (positive = good for that side). The heuristic is phase-aware — in the midgame it rewards corner
 * control, mobility and safe squares while deliberately ignoring raw disc count, and switches to
 * disc difference plus corners in the endgame. It is antisymmetric
 * ({@code evaluate(b, side) == -evaluate(b, opponent(side))}), which the negamax search relies on.
 *
 * <p>Stateless: the weight tables are {@code static} and shared; an instance holds no per-game
 * state.</p>
 */
final class Evaluate {

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

    /**
     * Total discs at which the evaluation switches from the midgame blend to endgame counting.
     */
    private static final int ENDGAME_THRESHOLD = 50;

    // Term weights. Endgame is dominated by disc difference and corners; the midgame blends
    // corners, mobility and the positional table (whose own magnitudes act as its weight).
    private static final int ENDGAME_DISC_WEIGHT = 100;
    private static final int ENDGAME_CORNER_WEIGHT = 1000;
    private static final int CORNER_WEIGHT = 25;
    private static final int MOBILITY_WEIGHT = 5;

    /**
     * Scale for the normalized mobility difference, giving a range of roughly [-100, 100].
     */
    private static final int MOBILITY_SCALE = 100;

    /**
     * The four corner squares as {row, col} pairs.
     */
    private static final int[][] CORNERS = {{0, 0}, {0, 7}, {7, 0}, {7, 7}};

    /**
     * Board edge length.
     */
    private static final int SIZE = 8;

    /**
     * Evaluates the position from {@code side}'s perspective. Antisymmetric
     * ({@code evaluate(b, side) == -evaluate(b, opponent(side))}); positive means good for
     * {@code side}. Uses the midgame blend (corners + mobility + position) until
     * {@link #ENDGAME_THRESHOLD} discs are on the board, then switches to disc difference plus corners.
     *
     * @param b    the position to score
     * @param side the perspective, {@link Board#BLACK} or {@link Board#WHITE}
     * @return the heuristic score (positive = good for {@code side})
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
     * Corner control: corners owned by {@code side} minus corners owned by {@code opp}. Corners can
     * never be flipped, so this is the most valuable positional feature. Range -4..+4; antisymmetric.
     *
     * @param b    the position
     * @param side the perspective side
     * @param opp  the opponent of {@code side}
     * @return (own corners) - (opponent corners)
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

    /**
     * Mobility: the normalized difference in legal-move counts, {@code MOBILITY_SCALE * (mine -
     * theirs) / (mine + theirs)}, roughly in [-100, 100]. Many moves for us and few for the opponent
     * is a strong midgame signal. Returns 0 when neither side can move (which also avoids dividing by
     * zero). Antisymmetric.
     *
     * @param b    the position
     * @param side the perspective side
     * @param opp  the opponent of {@code side}
     * @return the normalized mobility advantage of {@code side}
     */
    int mobilityScore(Board b, int side, int opp) {
        int myMoves = b.countLegalMoves(side);
        int oppMoves = b.countLegalMoves(opp);
        int total = myMoves + oppMoves;
        if (total == 0) {
            return 0;
        }
        return MOBILITY_SCALE * (myMoves - oppMoves) / total;
    }


    /**
     * Positional score: the sum of {@link #WEIGHTS} over {@code side}'s stones minus the sum over
     * {@code opp}'s stones -- a cheap static proxy for stability (corners good, X-/C-squares bad).
     * Antisymmetric.
     *
     * @param b    the position
     * @param side the perspective side
     * @param opp  the opponent of {@code side}
     * @return the positional advantage of {@code side}
     */
    int positionalScore(Board b, int side, int opp) {
        int score = 0;
        for (int i = 0; i < 64; i++) {
            int cell = b.getBoardCellFlat(i);
            if (cell == side) {
                score += WEIGHTS[i];
            } else if (cell == opp) {
                score -= WEIGHTS[i];
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
