package spieler.wendo;

import java.util.List;

/**
 * Throwaway self-test for Board rules. Not part of the player.
 */
public class BoardTest {

  static int pass = 0;
  static int fail = 0;

  static void check(String name, boolean cond) {
    if (cond) {
      pass++;
      System.out.println("PASS  " + name);
    } else {
      fail++;
      System.out.println("FAIL  " + name);
    }
  }

  public static void main(String[] args) {
    Board b = new Board();

    // Start position
    check("start: BLACK to move", b.getSideToMove() == Board.BLACK);
    check("start: 2 black", b.count(Board.BLACK) == 2);
    check("start: 2 white", b.count(Board.WHITE) == 2);

    // Black's opening moves are exactly (2,3)(3,2)(4,5)(5,4)
    List<int[]> mv = b.legalMoves(Board.BLACK);
    check("start: 4 legal moves for BLACK", mv.size() == 4);
    check("(2,3) legal", b.isLegal(2, 3, Board.BLACK));
    check("(3,2) legal", b.isLegal(3, 2, Board.BLACK));
    check("(4,5) legal", b.isLegal(4, 5, Board.BLACK));
    check("(5,4) legal", b.isLegal(5, 4, Board.BLACK));
    check("(3,3) illegal (occupied)", !b.isLegal(3, 3, Board.BLACK));
    check("(2,2) illegal (no bracket)", !b.isLegal(2, 2, Board.BLACK));

    // Apply (2,3): flips white at (3,3), side switches to WHITE
    b.applyMove(2, 3, Board.BLACK);
    check("after (2,3): (2,3)=BLACK", b.get(2, 3) == Board.BLACK);
    check("after (2,3): (3,3) flipped to BLACK", b.get(3, 3) == Board.BLACK);
    check("after (2,3): 4 black", b.count(Board.BLACK) == 4);
    check("after (2,3): 1 white", b.count(Board.WHITE) == 1);
    check("after (2,3): WHITE to move", b.getSideToMove() == Board.WHITE);

    // Illegal move rejected
    Board b2 = new Board();
    check("illegal apply rejected", !b2.applyMove(2, 2, Board.BLACK));
    check("illegal apply did not mutate", b2.count(Board.BLACK) == 2);

    // copy() is independent
    Board orig = new Board();
    Board cp = orig.copy();
    cp.applyMove(2, 3, Board.BLACK);
    check("copy independent", orig.count(Board.BLACK) == 2 && cp.count(Board.BLACK) == 4);

    // Not game over at start
    check("start not game over", !b.isGameOver());

    System.out.println("\n" + new Board());
    System.out.println("\nResult: " + pass + " passed, " + fail + " failed");
    if (fail > 0) {
      System.exit(1);
    }
  }
}
