package spieler.wendo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import spieler.Farbe;
import spieler.Zug;
import spieler.ZugException;

/**
 * Behavioural tests for the framework adapter. The board is private, so we assert only on what the
 * arena can observe: the Zug we return, and the desync contract.
 */
class SpielerTest {

  /**
   * Shallow depth keeps these tests fast; depth does not affect legality.
   */
  private static Spieler newPlayer(Farbe color) {
    Spieler s = new Spieler(2);
    s.neuesSpiel(color, 150);
    return s;
  }

  @Test
  void neuesSpiel_rejectsLeerColor() {
    Spieler s = new Spieler();
    // Farbe.LEER is not a playable side; we must reject it rather than mis-map it.
    assertThrows(IllegalArgumentException.class, () -> s.neuesSpiel(Farbe.LEER, 150));
  }

  // ---------------------------------------------------------------------
  // As BLACK we move first (vorherigerZug == null) and must play a legal opening
  // ---------------------------------------------------------------------

  @Test
  void berechneZug_blackFirstMoveIsALegalOpening() throws ZugException {
    Spieler s = newPlayer(Farbe.SCHWARZ);

    Zug move = s.berechneZug(null, 0L, 0L);

    assertFalse(move.isPassen());
    // From the start position BLACK's only legal moves are these four.
    assertTrue(isOpening(move));
  }

  // ---------------------------------------------------------------------
  // As WHITE we reply to the opponent's opening with a legal, non-pass move
  // ---------------------------------------------------------------------

  @Test
  void berechneZug_whiteRepliesToOpening() throws ZugException {
    Spieler s = newPlayer(Farbe.WEISS);

    // Black opened at (2,3); white always has a legal reply here.
    Zug reply = s.berechneZug(new Zug(2, 3), 0L, 0L);

    assertFalse(reply.isPassen());
  }

  // ---------------------------------------------------------------------
  // An illegal opponent move means our internal board has desynced -> ZugException
  // ---------------------------------------------------------------------

  @Test
  void berechneZug_throwsOnIllegalOpponentMove() {
    Spieler s = newPlayer(Farbe.WEISS);

    // (0,0) is not a legal BLACK move from the start position.
    assertThrows(ZugException.class, () -> s.berechneZug(new Zug(0, 0), 0L, 0L));
  }

  // ---------------------------------------------------------------------
  // An opponent PASS while they still have a legal move is a desync -> ZugException
  // ---------------------------------------------------------------------

  @Test
  void berechneZug_throwsWhenOpponentPassesWithLegalMoves() {
    Spieler s = newPlayer(Farbe.WEISS);

    // From the start position BLACK has four legal moves, so a BLACK pass cannot be
    // legitimate: our board would silently desync. We must reject it.
    assertThrows(ZugException.class, () -> s.berechneZug(Zug.passenZug(), 0L, 0L));
  }

  // ---------------------------------------------------------------------
  // setDepth guards against non-positive depths
  // ---------------------------------------------------------------------

  @Test
  void setDepth_ignoresNonPositiveValues() {
    Spieler s = new Spieler(); // DEFAULT_DEPTH = 6
    assertEquals(6, s.getDepth());

    s.setDepth(0);
    assertEquals(6, s.getDepth()); // unchanged

    s.setDepth(-3);
    assertEquals(6, s.getDepth()); // unchanged

    s.setDepth(4);
    assertEquals(4, s.getDepth()); // accepted
  }

  @Test
  void constructor_fallsBackToDefaultDepthOnInvalidInput() {
    // new Spieler(0)/(-1) must not leave the object with depth 0 (which would disable
    // the search). The field defaults to DEFAULT_DEPTH and the invalid value is ignored.
    assertEquals(6, new Spieler(0).getDepth());
    assertEquals(6, new Spieler(-1).getDepth());
  }

  // ---------------------------------------------------------------------
  // helper
  // ---------------------------------------------------------------------

  private static boolean isOpening(Zug z) {
    int r = z.getZeile();
    int c = z.getSpalte();
    return (r == 2 && c == 3) || (r == 3 && c == 2) || (r == 4 && c == 5) || (r == 5 && c == 4);
  }
}
