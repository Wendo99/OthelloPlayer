package spieler.wendo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TimeManagerTest {

  private final TimeManager tm = new TimeManager(150_000);

  @Test
  void earlyGame_splitsAcrossRemainingMoves() {
    // 60 empties -> 30 of my moves; 150000/30 * 0.9
    assertEquals(4500, tm.budgetForMove(0, 60));
  }

  @Test
  void endgame_concentratesRemainingTime() {
    // 2 empties -> 1 of my moves; 150000 * 0.9
    assertEquals(135_000, tm.budgetForMove(0, 2));
  }

  @Test
  void clockNearlyEmpty_fallsBackToFloor() {
    assertEquals(50, tm.budgetForMove(150_000, 60)); // remaining 0 -> floor
  }

  @Test
  void overspent_neverGoesNegative() {
    assertEquals(50, tm.budgetForMove(160_000, 10)); // remaining < 0 -> floor
  }
}
