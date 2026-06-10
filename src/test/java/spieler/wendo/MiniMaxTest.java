package spieler.wendo;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for the NegaScout search. These assert only invariants that hold for ANY antisymmetric
 * evaluator, so they remain valid after the disc-count placeholder is replaced by a real Evaluator.
 */
class MiniMaxTest {

  private MiniMax mm;

  @BeforeEach
  void setUp() {
    mm = new MiniMax();
  }

  // ---------------------------------------------------------------------
  // No legal move -> null (the caller is expected to pass)
  // ---------------------------------------------------------------------

  @Test
  void bestMove_returnsNullWhenNoLegalMove() {
    // A completely full board: no empty squares means no legal moves for either side.
    Board full = TestBoards.of(Board.BLACK,
        "XXXXXXXX"
            + "XXXXXXXX"
            + "XXXXXXXX"
            + "XXXXXXXX"
            + "XXXXXXXX"
            + "XXXXXXXX"
            + "XXXXXXXX"
            + "XXXXXXXX");

    assertNull(mm.bestMove(full, 4, Board.BLACK));
  }

  // ---------------------------------------------------------------------
  // Exactly one legal move -> that move (fully deterministic, evaluator-free)
  // ---------------------------------------------------------------------

  @Test
  void bestMove_returnsTheOnlyLegalMove() {
    // (0,0)=BLACK, (0,1)=WHITE. The only way to bracket the lone white stone is to
    // close it from the far side at (0,2): BLACK-WHITE-BLACK. No other square brackets.
    Board onlyOne = TestBoards.of(Board.BLACK,
        "XO......"
            + "........"
            + "........"
            + "........"
            + "........"
            + "........"
            + "........"
            + "........");

    assertEquals(1, onlyOne.legalMoves(Board.BLACK).size()); // guard the premise
    assertArrayEquals(new int[]{0, 2}, mm.bestMove(onlyOne, 4, Board.BLACK));
  }

  // ---------------------------------------------------------------------
  // Whatever it returns must be a legal move
  // ---------------------------------------------------------------------

  @Test
  void bestMove_alwaysReturnsALegalMove() {
    Board b = new Board(); // standard start, BLACK to move
    int[] best = mm.bestMove(b, 4, Board.BLACK);

    assertNotNull(best);
    assertTrue(isAmong(best, b.legalMoves(Board.BLACK)));
  }

  // ---------------------------------------------------------------------
  // Copy-make: searching must not mutate the position handed in
  // ---------------------------------------------------------------------

  @Test
  void bestMove_doesNotMutateInputBoard() {
    Board b = new Board();
    String before = b.toString();

    mm.bestMove(b, 4, Board.BLACK);

    assertEquals(before, b.toString());
  }

  // ---------------------------------------------------------------------
  // Robustness: a deep search from the start completes without error and
  // returns a legal move (exercises the recursion, scout re-search and the
  // internal pass branch across many nodes).
  // ---------------------------------------------------------------------

  @Test
  void bestMove_deepSearchCompletesWithLegalResult() {
    Board b = new Board();
    int[] best = assertDoesNotThrow(() -> mm.bestMove(b, 6, Board.BLACK));

    assertNotNull(best);
    assertTrue(isAmong(best, b.legalMoves(Board.BLACK)));
  }

  // ---------------------------------------------------------------------
  // helper
  // ---------------------------------------------------------------------

  private static boolean isAmong(int[] move, List<int[]> moves) {
    for (int[] m : moves) {
      if (m[0] == move[0] && m[1] == move[1]) {
        return true;
      }
    }
    return false;
  }
}
