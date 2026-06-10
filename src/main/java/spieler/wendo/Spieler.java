package spieler.wendo;

import spieler.Farbe;
import spieler.OthelloSpieler;
import spieler.Zug;
import spieler.ZugException;

public class Spieler implements OthelloSpieler {

  private Board board;
  private int myColor;
  private long totalTimeBudgetMs;
  private String name = "Wendo";
  private static final int DEFAULT_DEPTH = 6;

  /** Never spend less than this on a single move, even when the clock is nearly empty. */
  private static final long MIN_MOVE_MS = 50;

  private int depth;
  private MiniMax miniMax;

  public Spieler() {
    setDepth(DEFAULT_DEPTH);
  }

  public Spieler(int depth) {
    setDepth(depth);
  }

  /**
   * Ihr Spieler muss den berechneten Zug als Zug-Objekt zurückgeben. Zug-Objekte sind ein Paar
   * (Zeile, Spalte). Das erste Element sitzt oben links und hat den Wert (0,0). Das zweite
   * Element
   * der ersten Zeile dementsprechend (0,1). Das letzte Element unten rechts hat den Wert (7,7).
   * Wenn Sie passen müssen, liefern Sie bitte {@code Zug.passenZug()} zurück. Sollte die Berechnung
   * eines
   * Zuges nicht möglich sein (z.B. weil der vorhergehende Zug aus Sicht Ihrer Berechnung falsch
   * ist), so werfen Sie bitte eine ZugException. Wenn Sie als Erster ziehen dürfen, ist
   * vorherigerZug == null In zeitWeiss bzw. zeitSchwarz wird die bislang verbrauchte Zeit des
   * weissen bzw. schwarzen Spielers in ms angegeben.
   *
   * @param vorherigerZug The opponent's move in the previous turn, or null if we are to move
   *                      first.
   * @param zeitWeiss     The time taken by the white player so far, in milliseconds.
   * @param zeitSchwarz   The time taken by the black player so far, in milliseconds.
   * @return The move our player now wants to make.
   * @throws ZugException If it is not possible to calculate a move.
   */
  @Override
  public Zug berechneZug(Zug vorherigerZug, long zeitWeiss, long zeitSchwarz) throws ZugException {
    // 1. catch up on opponent's move
    replayOpponentMove(vorherigerZug);

    // 2. think: iterative deepening, bounded by the time we can afford for this move.
    int empties = countEmpties();
    long perMoveMs = perMoveBudgetMs(zeitWeiss, zeitSchwarz, empties);
    int maxDepth = Math.min(depth, empties); // can't look beyond the end of the game

    int[] best = miniMax.bestMove(board, maxDepth, myColor, perMoveMs);

    // 3. commit and report move
    return finalizeMove(best);
  }

  /**
   * Allocates how long the current move may take: the time we have left, divided by the number of
   * moves we still expect to make, minus a 10% reserve so a slightly-overrunning search never
   * flags the clock.
   *
   * @param zeitWeiss   time the white player has used so far, in ms
   * @param zeitSchwarz time the black player has used so far, in ms
   * @param empties     number of empty squares left on the board
   * @return the time budget for this move, in milliseconds
   */
  private long perMoveBudgetMs(long zeitWeiss, long zeitSchwarz, int empties) {
    long used = (myColor == Board.BLACK) ? zeitSchwarz : zeitWeiss;
    long remaining = totalTimeBudgetMs - used;
    int myMovesLeft = Math.max(1, empties / 2); // we make roughly half of the remaining moves
    long budget = (remaining / myMovesLeft) * 9 / 10;
    return Math.max(MIN_MOVE_MS, budget);
  }

  /**
   * @return the number of empty squares remaining on the board.
   */
  private int countEmpties() {
    return 64 - board.count(Board.BLACK) - board.count(Board.WHITE);
  }

  /**
   * Ein neues Spiel ist zu starten. Bringen Sie das Spielbrett in die Ausgangsposition und merken
   * Sie sich Ihre Farbe. Der Parameter bedenkzeitInSekunden gibt an, wie viel Bedenkzeit Ihrer
   * Maschine insgesamt zur Verfügung steht (für alle Züge zusammen).
   *
   * @param meineFarbe           The color we are playing in this game (BLACK or WHITE).
   * @param bedenkzeitInSekunden The total thinking time for all moves in this game, in seconds.
   */
  @Override
  public void neuesSpiel(Farbe meineFarbe, int bedenkzeitInSekunden) {
    this.board = new Board();
    this.miniMax = new MiniMax(new Evaluate());
    this.myColor = toBoardSide(meineFarbe);
    this.totalTimeBudgetMs = bedenkzeitInSekunden * 1000L;
  }

  /**
   * Helper for {@code berechneZug} that applies the opponent's previous move to our board, or
   * throws on desync.
   *
   * @param vorherigerZug The opponent's previous move, or null if we are to move first.
   * @throws ZugException If the opponent's move is illegal on our board.
   */
  private void replayOpponentMove(Zug vorherigerZug) throws ZugException {
    int oppColor = board.opponent(myColor);

    if (vorherigerZug != null) { // if null, we are black and move first
      if (vorherigerZug.isPassen()) {
        board.applyMove(0, Board.PASS, oppColor);
      } else {
        boolean isMoveValid = board.applyMove(vorherigerZug.getZeile(), vorherigerZug.getSpalte(),
            oppColor);
        if (!isMoveValid) {
          throw new ZugException("Opponent move illegal on our board: state desync.");
        }
      }
    }
  }

  /**
   * Applies our chosen move (or a pass when {@code best} is null) to our board and returns the
   * corresponding Zug object.
   *
   * @param best The best move as (row, col) pair, or null if we must pass.
   * @return The Zug object representing the move we just made.
   */
  private Zug finalizeMove(int[] best) throws ZugException {
    boolean isMoveValid;
    if (best == null) {
      isMoveValid = board.applyMove(0, Board.PASS, myColor);
      if (!isMoveValid) {
        throw new ZugException("We have no legal moves but failed to pass: state desync.");
      }
      return Zug.passenZug();
    }
    isMoveValid = board.applyMove(best[0], best[1], myColor);
    if (!isMoveValid) {
      throw new ZugException("Chosen move illegal on our board: state desync.");
    }
    return new Zug(best[0], best[1]);
  }


  /**
   * Helper for {@code neuesSpiel} that converts Farbe values to int.
   *
   * @param f The color we are playing, as a Farbe enum value.
   * @return The corresponding int value for the Board class.
   * @throws IllegalArgumentException If the color is not SCHWARZ or WEISS.
   */
  private static int toBoardSide(Farbe f) {
    return switch (f) {
      case SCHWARZ -> Board.BLACK;
      case WEISS -> Board.WHITE;
      default -> throw new IllegalArgumentException(
          "Player color must be SCHWARZ or WEISS, got " + f);
    };
  }

  /**
   * Liefert den Namen Ihres Spielers zurück
   *
   * @return Our player's name.
   */
  @Override
  public String meinName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public int getDepth() {
    return depth;
  }

  /**
   * Sets the maximum search depth that our player aims for when calculating a move.
   *
   * @param depth The maximum depth of search our player aims for when calculating a move.
   */
  public void setDepth(int depth) {
    if (depth >= 1) {
      this.depth = depth;
    }
  }
}

