package spieler.wendo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Internal Othello game state and rules engine.
 *
 * <p>This is the player's private board representation, completely independent of the
 * framework. The arena only ever exchanges single {@link spieler.Zug} objects; the full
 * position is reconstructed and maintained here.</p>
 *
 * <p>Representation of the board: a flat {@code int[64]} indexed as {@code row * 8 + col}.
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
     * The 8 directions as row/col deltas. Order is NW, N, NE, W, E, SW, S, SE (row-major / reading
     * order).
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

    private final int[] boardCells;

    /**
     * Side to move: BLACK or WHITE.
     */
    private int sideToMove;

    /**
     * Creates a new board with the standard Othello starting position.
     */
    Board() {
        boardCells = new int[NUMBER_CELLS];
        reset();
    }

    /**
     * Creates a new board with the given cell values and side to move. Used for copy().
     *
     * @param cells      the cell contents to copy, indexed {@code row*8+col}
     * @param sideToMove the side to move, {@link #BLACK} or {@link #WHITE}
     */
    private Board(int[] cells, int sideToMove) {
        boardCells = new int[NUMBER_CELLS];
        System.arraycopy(cells, 0, this.boardCells, 0, NUMBER_CELLS);
        this.sideToMove = sideToMove;
    }

    /**
     * <p>Construct a board from a full position. {@code cells} must have length {@code NUMBER_CELLS},
     * each entry being {@link #EMPTY}, {@link #BLACK} or {@link #WHITE}, indexed {@code row*8+col}.
     * Narrow, whole-position factory (no per-cell mutation API is exposed); used to set up specific
     * positions for tests and for reconstructing a position from move history.</p>
     *
     * @param cells      the full position, length {@link #NUMBER_CELLS}, indexed {@code row*8+col}
     * @param sideToMove the side to move, {@link #BLACK} or {@link #WHITE}
     * @return a new board holding a copy of {@code cells}
     * @throws IllegalArgumentException if {@code cells} is null or has the wrong length, or if
     *                                  {@code sideToMove} is neither {@link #BLACK} nor
     *                                  {@link #WHITE}
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
     * Returns an independent copy of this board (same cell contents and side to move), so a candidate
     * move can be explored on the copy without mutating the original. Used by the search (copy-make).
     *
     * @return a deep copy of this board
     */
    Board copy() {
        return new Board(boardCells, sideToMove);
    }

    /**
     * Converts a (row, col) coordinate to the flat array index {@code row * SIZE + col}.
     *
     * @param row row index, 0..7 (0 = top)
     * @param col column index, 0..7 (0 = left)
     * @return the index into {@link #boardCells}
     */
    private static int idx(int row, int col) {
        assert row >= 0 && row < SIZE && col >= 0 && col < SIZE : "out of range: " + row + "," + col;
        return row * SIZE + col;
    }

    /**
     * Returns the contents of the cell at ({@code row}, {@code col}).
     *
     * @param row row index, 0..7 (0 = top)
     * @param col column index, 0..7 (0 = left)
     * @return {@link #EMPTY}, {@link #BLACK} or {@link #WHITE}
     */
    int getBoardCell(int row, int col) {
        return boardCells[idx(row, col)];
    }

    /**
     * Enables flat access for high-performance loops (e.g., evaluation).
     */
    int getBoardCellFlat(int index) {
        return boardCells[index];
    }

    /**
     * Returns the side whose turn it is.
     *
     * @return {@link #BLACK} or {@link #WHITE}
     */
    int getSideToMove() {
        return sideToMove;
    }

    /**
     * Returns the opposing colour of {@code side}: {@link #WHITE} for {@link #BLACK} and vice versa.
     *
     * @param side {@link #BLACK} or {@link #WHITE}
     * @return the opponent's colour
     */
    int opponent(int side) {
        return side == BLACK ? WHITE : BLACK;
    }

    // ---------------------------------------------------------------------
    // Move generation
    // ---------------------------------------------------------------------

    /**
     * Returns whether {@code side} may legally place a stone at ({@code row}, {@code col}), i.e. the
     * cell is empty and the move brackets at least one line of opponent stones.
     *
     * @param row  row index, 0..7
     * @param col  column index, 0..7
     * @param side {@link #BLACK} or {@link #WHITE}
     * @return {@code true} if the move is legal
     */
    boolean isLegal(int row, int col, int side) {
        if (boardCells[idx(row, col)] != EMPTY) {
            return false;
        }
        int opp = opponent(side);
        for (int d = 0; d < DR.length; d++) {
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
     * Counts the legal moves without object allocation.
     * Essential for the performance of the evaluation function in the hand nodes.
     */
    int countLegalMoves(int side) {
        int count = 0;
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                if (isLegal(row, col, side)) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Returns all legal moves for {@code side}, each as an {@code int[]{row, col}}. An empty list
     * means the side has no move and must pass.
     *
     * @param side {@link #BLACK} or {@link #WHITE}
     * @return the list of legal moves (possibly empty)
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
     * Returns {@code true} if {@code side} has no legal move. Short-circuits on the first move
     * found,
     * so it is cheaper than {@link #legalMoves(int)} for the game-over check in
     * {@link #isGameOver()}.
     *
     * @param side the side to check, {@link #BLACK} or {@link #WHITE}
     * @return {@code true} if {@code side} has no legal move
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

    /**
     * Places a stone for {@code side} at ({@code row}, {@code col}), flips all bracketed opponent
     * stones, and advances the side to move. Pass a column of {@link #PASS} to pass (no stone is
     * placed; only the side to move switches).
     *
     * @param row  row index, 0..7 (ignored for a pass)
     * @param col  column index, 0..7, or {@link #PASS} to pass
     * @param side {@link #BLACK} or {@link #WHITE}
     * @return {@code true} if the move (or pass) was applied; {@code false} if an illegal, non-pass
     * move was requested, in which case the board is left unchanged
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
        for (int d = 0; d < DR.length; d++) {
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
     * Returns whether the game is over, i.e. neither side has a legal move.
     *
     * @return {@code true} if no legal move exists for either side
     */
    boolean isGameOver() {
        return hasNoLegalMove(BLACK) && hasNoLegalMove(WHITE);
    }

    /**
     * Counts the stones of {@code side} currently on the board.
     *
     * @param side {@link #BLACK} or {@link #WHITE}
     * @return the number of {@code side} stones
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
     * Number of empty squares remaining on the board. At the start position there are 60
     * (the 64 cells minus the 4 initial stones).
     *
     * @return the number of empty cells
     */
    int emptyCount() {
        return NUMBER_CELLS - count(BLACK) - count(WHITE);
    }

    /**
     * Returns a readable multi-line representation of the board (grid plus the current side to move),
     * intended for debugging and test output.
     *
     * @return a human-readable rendering of the position
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(" 0 1 2 3 4 5 6 7\n");
        for (int row = 0; row < SIZE; row++) {
            sb.append(row).append(' ');
            for (int col = 0; col < SIZE; col++) {
                int v = boardCells[idx(row, col)];
                char ch = switch (v) {
                    case BLACK -> 'X';
                    case WHITE -> 'O';
                    default -> '.';
                };
                sb.append(ch).append(' ');
            }
            sb.append('\n');
        }
        sb.append("side to move: ").append(sideToMove == BLACK ? "BLACK(X)" : "WHITE(O)");
        return sb.toString();
    }
}
