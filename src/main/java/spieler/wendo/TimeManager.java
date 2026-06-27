package spieler.wendo;

/**
 * Allocates thinking time across a game. Pure clock arithmetic: it knows nothing about the
 * framework, the board rules, or the search. Callers feed it elapsed time and a moves-left
 * estimate; it returns a per-move budget in milliseconds.
 */
final class TimeManager {

  /**
   * Never spend less than this on a single move, even when the clock is nearly empty.
   */
  private static final long MIN_MOVE_MS = 50;
  /**
   * We make roughly half the moves left on the board; the opponent makes the rest.
   */
  private static final int MOVE_SHARE_DIVISOR = 2;
  /**
   * Keep a 10% reserve so a slightly-overrunning search never flags the clock.
   */
  private static final int BUDGET_RESERVE_PERCENT = 90;

  private final long totalBudgetMs;

  TimeManager(long totalBudgetMs) {
    this.totalBudgetMs = totalBudgetMs;
  }

  /**
   * @param usedMs      time we have already spent this game, in ms
   * @param emptiesLeft empty squares remaining (a proxy for how many moves are left)
   * @return how long the current move may take, in ms (at least {@link #MIN_MOVE_MS})
   */
  long budgetForMove(long usedMs, int emptiesLeft) {
    long remaining = totalBudgetMs - usedMs;
    int myMovesLeft = Math.max(1, emptiesLeft / MOVE_SHARE_DIVISOR);
    long budget = (remaining / myMovesLeft) * BUDGET_RESERVE_PERCENT / 100;
    return Math.max(MIN_MOVE_MS, budget);
  }
}