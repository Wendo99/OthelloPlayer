package spieler.wendo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link Evaluate} heuristic, helper by helper.
 */
class EvaluateTest {

  private Evaluate eval;

  @BeforeEach
  void setUp() {
    eval = new Evaluate();
  }

  // ---------------------------------------------------------------------
  // cornerScore: (own corners) - (opponent corners), range -4..+4
  // ---------------------------------------------------------------------

  private static final String EMPTY_BOARD =
      "........"
          + "........"
          + "........"
          + "........"
          + "........"
          + "........"
          + "........"
          + "........";

  @Test
  void cornerScore_emptyCornersAreZero() {
    Board b = TestBoards.of(Board.BLACK, EMPTY_BOARD);
    assertEquals(0, eval.cornerScore(b, Board.BLACK, Board.WHITE));
  }

  @Test
  void cornerScore_countsOwnCornersPositive() {
    // BLACK holds all four corners.
    Board b = TestBoards.of(Board.BLACK,
        "X......X"
            + "........"
            + "........"
            + "........"
            + "........"
            + "........"
            + "........"
            + "X......X");

    assertEquals(4, eval.cornerScore(b, Board.BLACK, Board.WHITE));
  }

  @Test
  void cornerScore_oneEachCancelsOut() {
    // BLACK at (0,0), WHITE at (7,7): +1 and -1 net to 0.
    Board b = TestBoards.of(Board.BLACK,
        "X......."
            + "........"
            + "........"
            + "........"
            + "........"
            + "........"
            + "........"
            + ".......O");

    assertEquals(0, eval.cornerScore(b, Board.BLACK, Board.WHITE));
  }

  @Test
  void cornerScore_isAntisymmetric() {
    // BLACK at (0,0) and (0,7); WHITE at (7,0). BLACK perspective: +1+1-1 = +1.
    Board b = TestBoards.of(Board.BLACK,
        "X......X"
            + "........"
            + "........"
            + "........"
            + "........"
            + "........"
            + "........"
            + "O.......");

    int fromBlack = eval.cornerScore(b, Board.BLACK, Board.WHITE);
    int fromWhite = eval.cornerScore(b, Board.WHITE, Board.BLACK);

    assertEquals(1, fromBlack);
    assertEquals(-fromBlack, fromWhite); // the antisymmetry contract
  }

  // ---------------------------------------------------------------------
  // mobilityScore: 100 * (myMoves - oppMoves) / (myMoves + oppMoves)
  // ---------------------------------------------------------------------

  @Test
  void mobilityScore_returnsZeroWhenNeitherSideCanMove() {
    // Empty board: no opponent stones to bracket -> 0 moves each -> guard returns 0
    // (and no divide-by-zero).
    Board b = TestBoards.of(Board.BLACK, EMPTY_BOARD);
    assertEquals(0, eval.mobilityScore(b, Board.BLACK, Board.WHITE));
  }

  @Test
  void mobilityScore_symmetricStartIsZero() {
    // Standard start: BLACK and WHITE each have 4 legal moves -> equal -> 0.
    Board b = new Board();
    assertEquals(0, eval.mobilityScore(b, Board.BLACK, Board.WHITE));
  }

  @Test
  void mobilityScore_lopsidedReachesHundred() {
    // WHITE has none (its single flankable stone (3,0) is on the edge).
    Board b = TestBoards.of(Board.BLACK,
        "........"
            + "........"
            + "........"
            + "XOOOO..."
            + "........"
            + "........"
            + "........"
            + "........");

    assertEquals(1, b.legalMoves(Board.BLACK).size()); // guard the premise
    assertEquals(0, b.legalMoves(Board.WHITE).size());
    assertEquals(100, eval.mobilityScore(b, Board.BLACK, Board.WHITE));
  }

  @Test
  void mobilityScore_isAntisymmetric() {
    Board b = TestBoards.of(Board.BLACK,
        "........"
            + "........"
            + "........"
            + "XOOOO..."
            + "........"
            + "........"
            + "........"
            + "........");

    int fromBlack = eval.mobilityScore(b, Board.BLACK, Board.WHITE);
    int fromWhite = eval.mobilityScore(b, Board.WHITE, Board.BLACK);

    assertEquals(100, fromBlack);
    assertEquals(-fromBlack, fromWhite);
  }

  // ---------------------------------------------------------------------
  // positionalScore: sum of WEIGHTS[i] for own stones minus opponent stones
  // ---------------------------------------------------------------------

  @Test
  void positionalScore_emptyBoardIsZero() {
    Board b = TestBoards.of(Board.BLACK, EMPTY_BOARD);
    assertEquals(0, eval.positionalScore(b, Board.BLACK, Board.WHITE));
  }

  @Test
  void positionalScore_singleCornerScoresCornerWeight() {
    // BLACK alone in the top-left corner (0,0), weight +120.
    Board b = TestBoards.of(Board.BLACK,
        "X......."
            + "........"
            + "........"
            + "........"
            + "........"
            + "........"
            + "........"
            + "........");

    assertEquals(120, eval.positionalScore(b, Board.BLACK, Board.WHITE));
  }

  @Test
  void positionalScore_xSquareIsPenalised() {
    // BLACK on the X-square (1,1), weight -40: occupying it is bad for the owner.
    Board b = TestBoards.of(Board.BLACK,
        "........"
            + ".X......"
            + "........"
            + "........"
            + "........"
            + "........"
            + "........"
            + "........");

    assertEquals(-40, eval.positionalScore(b, Board.BLACK, Board.WHITE));
  }

  @Test
  void positionalScore_isAntisymmetric() {
    // BLACK owns a corner (+120), WHITE owns an X-square (-(-40) = +40 for BLACK).
    Board b = TestBoards.of(Board.BLACK,
        "X......."
            + "......O."
            + "........"
            + "........"
            + "........"
            + "........"
            + "........"
            + "........");

    int fromBlack = eval.positionalScore(b, Board.BLACK, Board.WHITE);
    int fromWhite = eval.positionalScore(b, Board.WHITE, Board.BLACK);

    assertEquals(160, fromBlack); // +120 (own corner) - (-40) (opp X-square)
    assertEquals(-fromBlack, fromWhite);
  }
}
