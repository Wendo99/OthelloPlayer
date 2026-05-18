package spieler.wendo;

/**
 * Test-only convenience for building a {@link Board} from a readable 8x8 layout.
 *
 * <p>This sugar (parsing a layout into the cell array) lives in the test tree on purpose: it
 * exists only for test readability and changes only for test reasons. The single privileged
 * operation - touching the position - is delegated to {@link Board#fromCells(int[], int)}.</p>
 *
 * <p>Layout: 8 rows of 8 chars, row 0 first. {@code '.'} = empty, {@code 'X'} = BLACK,
 * {@code 'O'} = WHITE. Whitespace/newlines between cells is ignored, so both a single
 * {@code "........XO......"}-style string and a multi-line block work.</p>
 */
final class TestBoards {

  private TestBoards() {
  }

  static Board of(int sideToMove, String layout) {
    String cleaned = layout.replaceAll("\\s", "");
    if (cleaned.length() != 64) {
      throw new IllegalArgumentException(
          "layout must contain exactly 64 cells, got " + cleaned.length());
    }
    int[] cells = new int[64];
    for (int i = 0; i < 64; i++) {
      switch (cleaned.charAt(i)) {
        case '.' -> cells[i] = Board.EMPTY;
        case 'X' -> cells[i] = Board.BLACK;
        case 'O' -> cells[i] = Board.WHITE;
        default -> throw new IllegalArgumentException(
            "illegal cell char '" + cleaned.charAt(i) + "' at index " + i);
      }
    }
    return Board.fromCells(cells, sideToMove);
  }
}
