package spieler.wendo;

import spieler.Farbe;
import spieler.OthelloSpieler;
import spieler.Zug;
import spieler.ZugException;

/**
 * Tournament Player of {@code spieler.wendo} : An adapter between the arena and the
 * internal search engine. Translates {@link Farbe}/{@link Zug} into the internal board
 * representation, maintains its own {@link Board}, and delegates move calculation to
 * {@link MiniMax} under time control by {@link TimeManager}.
 */
public final class Spieler implements OthelloSpieler {

  private static final String NAME = "wendo";
  private static final int DEFAULT_DEPTH = 100;

  private Board board;
  private int myColor;
  private int depth = DEFAULT_DEPTH;
  private MiniMax miniMax;
  private TimeManager timeManager;

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
   * @param vorherigerZug The opponent's move in the previous turn, or null if this player is to
   *                      move first.
   * @param zeitWeiss     The time taken by the white player so far, in milliseconds.
   * @param zeitSchwarz   The time taken by the black player so far, in milliseconds.
   * @return The move this player wants to make.
   * @throws ZugException If it is not possible to calculate a move.
   */
  @Override
  public Zug berechneZug(Zug vorherigerZug, long zeitWeiss, long zeitSchwarz) throws ZugException {
    replayOpponentMove(vorherigerZug);
    int empties = board.emptyCount();
    long used = (myColor == Board.BLACK) ? zeitSchwarz : zeitWeiss;
    long perMoveMs = timeManager.budgetForMove(used, empties);
    int maxDepth = Math.min(depth, empties);

    int[] best = miniMax.bestMove(board, maxDepth, myColor, perMoveMs).orElse(null);
    return finalizeMove(best);
  }

  /**
   * Ein neues Spiel ist zu starten. Bringen Sie das Spielbrett in die Ausgangsposition und merken
   * Sie sich Ihre Farbe. Der Parameter bedenkzeitInSekunden gibt an, wie viel Bedenkzeit Ihrer
   * Maschine insgesamt zur Verfügung steht (für alle Züge zusammen).
   *
   * @param meineFarbe           The color this player is playing in this game (BLACK or
   *                             WHITE).
   * @param bedenkzeitInSekunden The total thinking time for all moves in this game, in seconds.
   */
  @Override
  public void neuesSpiel(Farbe meineFarbe, int bedenkzeitInSekunden) {
    this.board = new Board();
    this.miniMax = new MiniMax(new Evaluate());
    this.myColor = toBoardSide(meineFarbe);
    this.timeManager = new TimeManager(bedenkzeitInSekunden * 1000L);
  }

  /**
   * Helper for {@code berechneZug} that applies the opponent's previous move to
   * this player's board, or throws on desync.
   *
   * @param vorherigerZug The opponent's previous move, or null if this player is to
   *                      move first.
   * @throws ZugException If the opponent's move is illegal on this player's board.
   */
  private void replayOpponentMove(Zug vorherigerZug) throws ZugException {
    int oppColor = board.opponent(myColor);

    if (vorherigerZug != null) {
      if (vorherigerZug.isPassen()) {
        if (!board.legalMoves(oppColor).isEmpty()) {
          throw new ZugException("Opponent passed but had a legal move: state desync.");
        }
        board.applyMove(0, Board.PASS, oppColor);
      } else {
        boolean isMoveValid = board.applyMove(vorherigerZug.getZeile(), vorherigerZug.getSpalte(),
            oppColor);
        if (!isMoveValid) {
          throw new ZugException("Opponent move illegal on this player's board: state desync.");
        }
      }
    }
  }

  /**
   * Applies this player's chosen move (or a pass when {@code best} is null) to
   * this player's board and returns the corresponding Zug object.
   *
   * @param best The best move as (row, col) pair, or null if this player must pass.
   * @return The Zug object representing the move we just made.
   */
  private Zug finalizeMove(int[] best) throws ZugException {
    if (best == null) {
      boolean ok = board.applyMove(0, Board.PASS, myColor);
      if (!ok) {
        throw new ZugException(
            "This player has no legal moves but failed to pass: state desync.");
      }
      return Zug.passenZug();
    }
    boolean ok = board.applyMove(best[0], best[1], myColor);
    if (!ok) {
      throw new ZugException("Chosen move illegal on this player's board: state desync.");
    }
    return new Zug(best[0], best[1]);
  }

  /**
   * Helper for {@code neuesSpiel} that converts Farbe values to int.
   *
   * @param f The color this player is playing, as a Farbe enum value.
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
   * @return this player's name.
   */
  @Override
  public String meinName() {
    return NAME;
  }

  /**
   * Get the player's search depth.
   *
   * @return the search depth
   */
  int getDepth() {
    return depth;
  }

  /**
   * Sets the maximum search depth for the player. Values below 1 are invalid and are
   * ignored. The previous depth is kept (initially {@link #DEFAULT_DEPTH}). This keeps the
   * player usable even when constructed with a bad depth from the arena config, instead of failing
   * instantiation.
   *
   * @param depth the desired maximum search depth; ignored if less than 1
   */
  void setDepth(int depth) {
    if (depth >= 1) {
      this.depth = depth;
    }
  }
}
