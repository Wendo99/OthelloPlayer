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
  private int depth;

  public Spieler() {
    setDepth(DEFAULT_DEPTH);
  }

  public Spieler(int depth) {
    setDepth(depth);
  }

  /**
   * Ihr Spieler muss den berechneten Zug als Zug-Objekt zurückgeben. Zug-Objekte sind ein Paar
   * (Zeile, Spalte). Das erste Element sitzt oben links und hat den Wert (0,0). Das zweite Element
   * der ersten Zeile dementsprechend (0,1). Das letzte Element unten rechts hat den Wert (7,7).
   * Wenn Sie passen müssen, liefern Sie bitte Zug.setPassen() zurück. Sollte die Berechnung eines
   * Zuges nicht möglich sein (z.B. weil der vorhergehende Zug aus Sicht Ihrer Berechnung falsch
   * ist), so werfen Sie bitte eine ZugException. Wenn Sie als Erster ziehen dürfen, ist
   * vorherigerZug == null In zeitWeiss bzw. zeitSchwarz wird die bislang verbrauchte Zeit des
   * weissen bzw. schwarzen Spielers in ms angegeben.
   */
  @Override
  public Zug berechneZug(Zug vorherigerZug, long zeitWeiss, long zeitSchwarz) throws ZugException {
    // TODO: calculate own turn
    return null;
  }

  /**
   * Ein neues Spiel ist zu starten. Bringen Sie das Spielbrett in die Ausgangsposition und merken
   * Sie sich Ihre Farbe. Der Parameter bedenkzeitInSekunden gibt an, wie viel Bedenkzeit Ihrer
   * Maschine insgesamt zur Verfügung steht (für alle Züge zusammen).
   *
   * @param meineFarbe
   * @param bedenkzeitInSekunden
   */
  @Override
  public void neuesSpiel(Farbe meineFarbe, int bedenkzeitInSekunden) {
    this.board = new Board();
    this.myColor = toBoardSide(meineFarbe);
    this.totalTimeBudgetMs = bedenkzeitInSekunden * 1000L;
  }

  /**
   *
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

  public void setDepth(int depth) {
    this.depth = depth;
  }
}

