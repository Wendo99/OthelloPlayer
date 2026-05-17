package spieler.wendo;

import java.util.ArrayList;
import java.util.List;

/**
 * Internal Othello game state and rules engine.
 *
 * <p>This is OUR private representation, completely independent of the
 * framework. The arena only ever exchanges single {@link spieler.Zug} objects with us; we
 * reconstruct and maintain the full position here so the search has something to work on.</p>
 *
 * <p>Representation: a flat {@code int[64]} indexed as {@code row * 8 + col}.
 * Row 0 / col 0 is the top-left corner, matching the framework's {@code Zug} coordinate
 * convention.</p>
 */
public class Board {

  public static final int EMPTY = 0;
  public static final int BLACK = 1; // Farbe.SCHWARZ, moves first
  public static final int WHITE = 2; // Farbe.WEISS

  public static final int SIZE = 8;
  public static final int CELLS = SIZE * SIZE;

  /**
   * Sentinel column used to represent a "pass" move.
   */
  public static final int PASS = -1;

  /**
   * The 8 directions as row/col deltas.
   */
  private static final int[] DR = {-1, -1, -1, 0, 0, 1, 1, 1};
  private static final int[] DC = {-1, 0, 1, -1, 1, -1, 0, 1};

  private final int[] cells = new int[CELLS];

  /**
   * Side to move: BLACK or WHITE.
   */
  private int sideToMove;

  public Board() {
    reset();
  }

  private Board(int[] cells, int sideToMove) {
    System.arraycopy(cells, 0, this.cells, 0, CELLS);
    this.sideToMove = sideToMove;
  }

  /**
   * Reset to the standard Othello start position; BLACK to move.
   */
  public void reset() {
    for (int i = 0; i < CELLS; i++) {
      cells[i] = EMPTY;
    }
    cells[idx(3, 3)] = WHITE;
    cells[idx(3, 4)] = BLACK;
    cells[idx(4, 3)] = BLACK;
    cells[idx(4, 4)] = WHITE;
    sideToMove = BLACK;
  }

  public Board copy() {
    return new Board(cells, sideToMove);
  }

  public static int idx(int row, int col) {
    return row * SIZE + col;
  }

  public int get(int row, int col) {
    return cells[idx(row, col)];
  }

  public int getSideToMove() {
    return sideToMove;
  }

  public void setSideToMove(int side) {
    this.sideToMove = side;
  }

  public static int opponent(int side) {
    return side == BLACK ? WHITE : BLACK;
  }

  // ---------------------------------------------------------------------
  // Move generation
  // ---------------------------------------------------------------------

  /**
   * True if {@code side} can legally place a stone at (row, col).
   */
  public boolean isLegal(int row, int col, int side) {
    if (cells[idx(row, col)] != EMPTY) {
      return false;
    }
    int opp = opponent(side);
    for (int d = 0; d < 8; d++) {
      int r = row + DR[d];
      int c = col + DC[d];
      boolean sawOpp = false;
      while (r >= 0 && r < SIZE && c >= 0 && c < SIZE && cells[idx(r, c)] == opp) {
        sawOpp = true;
        r += DR[d];
        c += DC[d];
      }
      if (sawOpp && r >= 0 && r < SIZE && c >= 0 && c < SIZE
          && cells[idx(r, c)] == side) {
        return true;
      }
    }
    return false;
  }

  /**
   * All legal moves for {@code side} as int[]{row, col}. Empty list means the side must pass.
   */
  public List<int[]> legalMoves(int side) {
    List<int[]> moves = new ArrayList<>();
    for (int row = 0; row < SIZE; row++) {
      for (int col = 0; col < SIZE; col++) {
        if (isLegal(row, col, side)) {
          moves.add(new int[]{row, col});
        }
      }
    }
    return moves;
  }

  public boolean hasLegalMove(int side) {
    for (int row = 0; row < SIZE; row++) {
      for (int col = 0; col < SIZE; col++) {
        if (isLegal(row, col, side)) {
          return true;
        }
      }
    }
    return false;
  }

  // ---------------------------------------------------------------------
  // Applying moves
  // ---------------------------------------------------------------------

  /**
   * Place a stone for {@code side} at (row, col), flip all bracketed opponent stones, and advance
   * the side to move. The move must be legal. Use {@code col == PASS} to pass.
   *
   * @return false if an illegal (non-pass) move was requested.
   */
  public boolean applyMove(int row, int col, int side) {
    if (col == PASS) {
      sideToMove = opponent(side);
      return true;
    }
    if (!isLegal(row, col, side)) {
      return false;
    }
    int opp = opponent(side);
    cells[idx(row, col)] = side;
    for (int d = 0; d < 8; d++) {
      int r = row + DR[d];
      int c = col + DC[d];
      int n = 0;
      while (r >= 0 && r < SIZE && c >= 0 && c < SIZE && cells[idx(r, c)] == opp) {
        r += DR[d];
        c += DC[d];
        n++;
      }
      if (n > 0 && r >= 0 && r < SIZE && c >= 0 && c < SIZE
          && cells[idx(r, c)] == side) {
        r = row + DR[d];
        c = col + DC[d];
        for (int k = 0; k < n; k++) {
          cells[idx(r, c)] = side;
          r += DR[d];
          c += DC[d];
        }
      }
    }
    sideToMove = opp;
    return true;
  }

  // ---------------------------------------------------------------------
  // Terminal / scoring
  // ---------------------------------------------------------------------

  /**
   * Game over when neither side has a legal move.
   */
  public boolean isGameOver() {
    return !hasLegalMove(BLACK) && !hasLegalMove(WHITE);
  }

  public int count(int side) {
    int n = 0;
    for (int i = 0; i < CELLS; i++) {
      if (cells[i] == side) {
        n++;
      }
    }
    return n;
  }

  /**
   * Disc difference from {@code side}'s perspective (positive = ahead).
   */
  public int discDiff(int side) {
    return count(side) - count(opponent(side));
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("  0 1 2 3 4 5 6 7\n");
    for (int row = 0; row < SIZE; row++) {
      sb.append(row).append(' ');
      for (int col = 0; col < SIZE; col++) {
        int v = cells[idx(row, col)];
        sb.append(v == BLACK ? 'X' : v == WHITE ? 'O' : '.').append(' ');
      }
      sb.append('\n');
    }
    sb.append("side to move: ").append(sideToMove == BLACK ? "BLACK(X)" : "WHITE(O)");
    return sb.toString();
  }
}
