package spieler.wendo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Internal Othello game state and rules engine.
 *
 * <p>This is our private player board representation, completely independent of the
 * framework. The arena only ever exchanges single {@link spieler.Zug} objects with us; we
 * reconstruct and maintain the full position here so the search has something to work on.</p>
 *
 * <p>Representation: a flat {@code int[64]} indexed as {@code row * 8 + col}.
 * Row 0 / col 0 is the top-left corner, matching the framework's {@code Zug} coordinate
 * convention.</p>
 */
class Board {

  static final int EMPTY = 0;
  static final int BLACK = 1; // Farbe.SCHWARZ, moves first
  static final int WHITE = 2; // Farbe.WEISS

  private static final int SIZE = 8;
  private static final int NUMBER_CELLS = SIZE * SIZE;

  /**
   * Sentinel column used to represent a "pass" move.
   */
  static final int PASS = -1;

  /**
   * The 8 directions as row/col deltas. Order is NW, N, NE, W, E, SW, S, SE (counterclockwise).
   * Up-Left    DR: -1 ,  DC: -1
   * Up         DR: -1 ,  DC:  0
   * Up-Right   DR: -1 ,  DC:  1
   * Left       DR:  0 ,  DC: -1
   * Right      DR:  0 ,  DC:  1
   * Down-Left  DR:  1 ,  DC: -1
   * Down       DR:  1 ,  DC:  0
   * Down-Right DR:  1 ,  DC:  1
   */
  private static final int[] DR = {-1, -1, -1, 0, 0, 1, 1, 1};
  private static final int[] DC = {-1, 0, 1, -1, 1, -1, 0, 1};

  private final int[] boardCells = new int[NUMBER_CELLS];

  /**
   * Side to move: BLACK or WHITE.
   */
  private int sideToMove;

  /**
   * Creates a new board with the standard Othello starting position.
   */
  public Board() {
    reset();
  }

  /**
   * Creates a new board with the given cell values and side to move. Used for copy().
   */
  private Board(int[] cells, int sideToMove) {
    System.arraycopy(cells, 0, this.boardCells, 0, NUMBER_CELLS);
    this.sideToMove = sideToMove;
  }

  /**
   * Construct a board from a full position. {@code cells} must have length {@code NUMBER_CELLS},
   * each entry being {@link #EMPTY}, {@link #BLACK} or {@link #WHITE}, indexed {@code row*8+col}.
   * Narrow, whole-position factory (no per-cell mutation API is exposed); used to set up specific
   * positions for tests and for reconstructing a position from move history.
   */
  static Board fromCells(int[] cells, int sideToMove) {
    if (cells == null || cells.length != NUMBER_CELLS) {
      throw new IllegalArgumentException("cells must have length " + NUMBER_CELLS);
    }
    if (sideToMove != BLACK && sideToMove != WHITE) {
      throw new IllegalArgumentException("sideToMove must be BLACK or WHITE");
    }
    return new Board(cells, sideToMove);
  }

  /**
   * Reset to the standard Othello start position; BLACK to move.
   */
  private void reset() {
    Arrays.fill(boardCells, EMPTY);
    boardCells[idx(3, 3)] = WHITE;
    boardCells[idx(3, 4)] = BLACK;
    boardCells[idx(4, 3)] = BLACK;
    boardCells[idx(4, 4)] = WHITE;
    sideToMove = BLACK;
  }

  /**
   * Allows the board to be used without altering the original
   */
  Board copy() {
    return new Board(boardCells, sideToMove);
  }


  /**
   * Convert (row, col) to flat index.
   */
  private int idx(int row, int col) {
    return row * SIZE + col;
  }

  /**
   * Get the value of a certain cell defined by row and col: EMPTY, BLACK, or WHITE.
   */
  int getBoardCell(int row, int col) {
    return boardCells[idx(row, col)];
  }

  /**
   * Get the side to move: BLACK or WHITE.
   */
  int getSideToMove() {
    return sideToMove;
  }

  /**
   * Set {@code side} to move: BLACK or WHITE.
   */
  void setSideToMove(int side) {
    this.sideToMove = side;
  }

  /**
   * Get the opponents color by determining {@code side} color.
   */
  int opponent(int side) {
    return side == BLACK ? WHITE : BLACK;
  }

  // ---------------------------------------------------------------------
  // Move generation
  // ---------------------------------------------------------------------

  /**
   * True if {@code side} can legally place a stone at (row, col).
   */
  boolean isLegal(int row, int col, int side) {
    if (boardCells[idx(row, col)] != EMPTY) {
      return false;
    }
    int opp = opponent(side);
    for (int d = 0; d < 8; d++) {
      int r = row + DR[d];
      int c = col + DC[d];
      boolean sawOpp = false;
      while (r >= 0 && r < SIZE && c >= 0 && c < SIZE && boardCells[idx(r, c)] == opp) {
        sawOpp = true;
        r += DR[d];
        c += DC[d];
      }
      if (sawOpp && r >= 0 && r < SIZE && c >= 0 && c < SIZE
          && boardCells[idx(r, c)] == side) {
        return true;
      }
    }
    return false;
  }

  /**
   * All legal moves for {@code side} as int[]{row, col}. Empty list means the side must pass.
   */
  List<int[]> legalMoves(int side) {
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

  /**
   * Evaluates whether {@code side} has NO legal move available.
   * This name matches call sites that check for the absence of moves.
   */
  private boolean hasNoLegalMove(int side) {
    for (int row = 0; row < SIZE; row++) {
      for (int col = 0; col < SIZE; col++) {
        if (isLegal(row, col, side)) {
          return false; // found a legal move
        }
      }
    }
    return true; // no legal moves found
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
  boolean applyMove(int row, int col, int side) {
    if (col == PASS) {
      sideToMove = opponent(side);
      return true;
    }
    if (!isLegal(row, col, side)) {
      return false;
    }
    int opp = opponent(side);
    boardCells[idx(row, col)] = side;
    for (int d = 0; d < 8; d++) {
      int r = row + DR[d];
      int c = col + DC[d];
      int n = 0;
      while (r >= 0 && r < SIZE && c >= 0 && c < SIZE && boardCells[idx(r, c)] == opp) {
        r += DR[d];
        c += DC[d];
        n++;
      }
      if (n > 0 && r >= 0 && r < SIZE && c >= 0 && c < SIZE
          && boardCells[idx(r, c)] == side) {
        r = row + DR[d];
        c = col + DC[d];
        for (int k = 0; k < n; k++) {
          boardCells[idx(r, c)] = side;
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
  boolean isGameOver() {
    // Game is over only when both sides have no legal moves.
    // i.e. neither BLACK nor WHITE has a legal move available.
    return hasNoLegalMove(BLACK) && hasNoLegalMove(WHITE);
  }

  /**
   * Number of stones on the board for {@code side}.
   */
  int count(int side) {
    int n = 0;
    for (int i = 0; i < NUMBER_CELLS; i++) {
      if (boardCells[i] == side) {
        n++;
      }
    }
    return n;
  }

  /**
   * Disc difference from {@code side}'s perspective (positive = ahead).
   */
  int discDiff(int side) {
    return count(side) - count(opponent(side));
  }

  /**
   * Returns a readable string representation of the board state, showing the grid and current side
   * to move.
   */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(" 0 1 2 3 4 5 6 7\n");
    for (int row = 0; row < SIZE; row++) {
      sb.append(row).append(' ');
      for (int col = 0; col < SIZE; col++) {
        int v = boardCells[idx(row, col)];
        char nonBlackChar = v == WHITE ? 'O' : '.';
        sb.append(v == BLACK ? 'X' : nonBlackChar).append(' ');
      }
      sb.append('\n');
    }
    sb.append("side to move: ").append(sideToMove == BLACK ? "BLACK(X)" : "WHITE(O)");
    return sb.toString();
  }
}
