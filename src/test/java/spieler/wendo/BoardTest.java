package spieler.wendo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Throwaway self-test for Board rules. Not part of the player.
 */
class BoardTest {

  private Board b;

  @BeforeEach
  void setUp() {
    b = new Board();
  }

  // ---------------------------------------------------------------------
  // Start position
  // ---------------------------------------------------------------------

  @Test
  void start_sideAndCounts() {
    assertEquals(Board.BLACK, b.getSideToMove());
    assertEquals(2, b.count(Board.BLACK));
    assertEquals(2, b.count(Board.WHITE));
  }

  @Test
  void start_blackHasFourLegalMoves() {
    assertEquals(4, b.legalMoves(Board.BLACK).size());
    assertTrue(b.isLegal(2, 3, Board.BLACK));
    assertTrue(b.isLegal(3, 2, Board.BLACK));
    assertTrue(b.isLegal(4, 5, Board.BLACK));
    assertTrue(b.isLegal(5, 4, Board.BLACK));
  }

  @Test
  void start_rejectsIllegalSquares() {
    assertFalse(b.isLegal(3, 3, Board.BLACK)); // occupied
    assertFalse(b.isLegal(2, 2, Board.BLACK)); // no bracket
  }

  @Test
  void start_isNotGameOver() {
    assertFalse(b.isGameOver());
  }

  // ---------------------------------------------------------------------
  // Apply (2,3): flips white at (3,3), side switches to WHITE
  // ---------------------------------------------------------------------

  @Test
  void applyMove_flipsAndSwitchesSide() {
    assertTrue(b.applyMove(2, 3, Board.BLACK));
    assertEquals(Board.BLACK, b.getBoardCell(2, 3));
    assertEquals(Board.BLACK, b.getBoardCell(3, 3)); // flipped
    assertEquals(4, b.count(Board.BLACK));
    assertEquals(1, b.count(Board.WHITE));
    assertEquals(Board.WHITE, b.getSideToMove());
  }

  // ---------------------------------------------------------------------
  // Illegal move rejected
  // ---------------------------------------------------------------------

  @Test
  void illegalMove_isRejectedAndDoesNotMutate() {
    assertFalse(b.applyMove(2, 2, Board.BLACK));
    assertEquals(2, b.count(Board.BLACK));
  }

  // ---------------------------------------------------------------------
  // copy() is independent
  // ---------------------------------------------------------------------

  @Test
  void copy_isIndependentOfOriginal() {
    Board cp = b.copy();
    cp.applyMove(2, 3, Board.BLACK);
    assertEquals(2, b.count(Board.BLACK));   // original untouched
    assertEquals(4, cp.count(Board.BLACK));  // copy advanced
  }

  // ---------------------------------------------------------------------
  // No edge wrap flips
  // ---------------------------------------------------------------------

  @Test
  void applyMove_noEdgeWrapFlips() {
    // row 0: BLACK plays (0,0); (0,1..7) are O (WHITE). Scanning right runs
    // off column 7. The flat-array trap: one step past (0,7) is index 8 ==
    // (1,0). A BLACK stone is placed there, so an implementation missing the
    // `c < SIZE` bound would treat it as a closing stone and wrongly flip the
    // whole of row 0. Correct code finds no real bracket -> move is illegal
    // and nothing flips.
    Board edge = TestBoards.of(Board.BLACK,
        ".OOOOOOO"
            + "X......."
            + "........"
            + "........"
            + "........"
            + "........"
            + "........"
            + "........");

    // No legal direction brackets (0,0): the rightward run of O's falls off
    // the edge instead of being closed by an own stone.
    assertFalse(edge.applyMove(0, 0, Board.BLACK));

    // (0,0) stays empty - the rejected move must not mutate.
    assertEquals(0, edge.getBoardCell(0, 0));

    // None of row 0 wrapped/flipped: every O is still WHITE.
    for (int col = 1; col < 8; col++) {
      assertEquals(Board.WHITE, edge.getBoardCell(0, col));
    }

    // The trap stone at (1,0) is untouched.
    assertEquals(Board.BLACK, edge.getBoardCell(1, 0));
  }

  // ---------------------------------------------------------------------
  // Illegal: opponent stone adjacent, but the run is never closed
  // ---------------------------------------------------------------------

  @Test
  void illegalMove_opponentAdjacentButNotClosed() {
    // BLACK plays (3,3). Right neighbor (3,4) is WHITE (opponent), so the
    // scan starts a run - but (3,5) is empty, so the run is never closed by
    // a BLACK stone. This is the distinct "sawOpp but no terminator" branch,
    // different from "occupied" or "no opponent adjacent at all".
    Board b1 = TestBoards.of(Board.BLACK,
        "........"
            + "........"
            + "........"
            + "....O..."   // (3,4) = WHITE, (3,3) and (3,5) empty
            + "........"
            + "........"
            + "........"
            + "........");

    assertFalse(b1.isLegal(3, 3, Board.BLACK));
    assertFalse(b1.applyMove(3, 3, Board.BLACK));

    // Rejected move must not mutate: (3,3) still empty, (3,4) still WHITE.
    assertEquals(0, b1.getBoardCell(3, 3));
    assertEquals(Board.WHITE, b1.getBoardCell(3, 4));
    // Side to move unchanged after a rejected move.
    assertEquals(Board.BLACK, b1.getSideToMove());
  }

  // ---------------------------------------------------------------------
  // Pass: applyMove with the PASS sentinel column
  // ---------------------------------------------------------------------

  @Test
  void applyMove_passSwitchesSideWithoutMutating() {
    // Passing must succeed, flip the
    // side to move, and leave every stone untouched.
    Board b2 = new Board(); // standard start, BLACK to move, 2/2 stones

    assertTrue(b2.applyMove(0, Board.PASS, Board.BLACK));

    assertEquals(Board.WHITE, b2.getSideToMove());
    assertEquals(2, b2.count(Board.BLACK));
    assertEquals(2, b2.count(Board.WHITE));
  }

  // ---------------------------------------------------------------------
  // Multi-direction flip
  // ---------------------------------------------------------------------

  @Test
  void applyMove_multiDirectionFlip() {
    // BLACK plays (4,4). It brackets WHITE in TWO directions at once:
    //   East : (4,5)=O (4,6)=O closed by (4,7)=X  -> flips (4,5),(4,6)
    //   South: (5,4)=O (6,4)=O closed by (7,4)=X  -> flips (5,4),(6,4)
    // Verifies applyMove flips every bracketed line, not just the first.
    Board m = TestBoards.of(Board.BLACK,
        "........"
            + "........"
            + "........"
            + "........"
            + ".....OOX"   // row 4: (4,5)=O (4,6)=O (4,7)=X, (4,4) empty
            + "....O..."   // row 5: (5,4)=O
            + "....O..."   // row 6: (6,4)=O
            + "....X...");  // row 7: (7,4)=X

    assertTrue(m.applyMove(4, 4, Board.BLACK));

    // Placed stone.
    assertEquals(Board.BLACK, m.getBoardCell(4, 4));
    // East line flipped.
    assertEquals(Board.BLACK, m.getBoardCell(4, 5));
    assertEquals(Board.BLACK, m.getBoardCell(4, 6));
    // South line flipped.
    assertEquals(Board.BLACK, m.getBoardCell(5, 4));
    assertEquals(Board.BLACK, m.getBoardCell(6, 4));
    // Closing stones unchanged.
    assertEquals(Board.BLACK, m.getBoardCell(4, 7));
    assertEquals(Board.BLACK, m.getBoardCell(7, 4));

    // All four O's were captured; none remain.
    assertEquals(7, m.count(Board.BLACK));
    assertEquals(0, m.count(Board.WHITE));
    assertEquals(Board.WHITE, m.getSideToMove());
  }

  // ---------------------------------------------------------------------
  // emptyCount: total cells minus stones of both colours
  // ---------------------------------------------------------------------

  @Test
  void emptyCount_startAndFullBoard() {
    // Start: 4 stones placed -> 60 empty.
    assertEquals(60, b.emptyCount());

    // A fully occupied board -> 0 empty.
    Board full = TestBoards.of(Board.BLACK,
        "XXXXXXXX"
            + "XXXXXXXX"
            + "XXXXXXXX"
            + "XXXXXXXX"
            + "XXXXXXXX"
            + "XXXXXXXX"
            + "XXXXXXXX"
            + "XXXXXXXX");
    assertEquals(0, full.emptyCount());
  }
}
