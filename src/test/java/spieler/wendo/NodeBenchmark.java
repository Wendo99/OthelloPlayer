package spieler.wendo;

import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Measures nodes searched at fixed depth. Deterministic — one run per code version.
 */
final class NodeBenchmark {

  private static final Logger LOGGER = LogManager.getLogger();

  public static void main(String[] args) {
    MiniMax mm = new MiniMax(new Evaluate());
    bench(mm, "start      ", new Board());
    bench(mm, "midgame(20)", advance(new Board()));
  }

  private static void bench(MiniMax mm, String label, Board b) {
    int side = b.getSideToMove();
    for (int depth : new int[]{8, 10}) {
      mm.bestMove(b, depth, side);                 // warm-up run — discard its time
      long nodes = mm.getNodeCount();
      long best = Long.MAX_VALUE;
      for (int r = 0; r < 5; r++) {                // measure a few times
        long t0 = System.nanoTime();
        mm.bestMove(b, depth, side);
        best = Math.min(best, System.nanoTime() - t0);   // min = least OS/GC interference
      }
      LOGGER.info("{} depth {}: {} nodes, {} ms, {} knps",
          label, depth, nodes, best / 1_000_000, nodes * 1_000_000 / Math.max(best, 1));
    }
  }

  /**
   * Deterministically walk the game forward by playing the middle legal move each ply (the middle
   * move avoids the degenerate top-left clustering that "always first" produces). Afterward it
   * skips any trailing forced pass, so the returned position is guaranteed to have moves to search
   * (unless the game is already over) — otherwise the benchmark would measure 0 nodes.
   */
  private static Board advance(Board b) {
    for (int i = 0; i < 20; i++) {
      int side = b.getSideToMove();
      List<int[]> moves = b.legalMoves(side);
      if (moves.isEmpty()) {
        b.applyMove(0, Board.PASS, side);
      } else {
        int[] mv = moves.get(moves.size() / 2);
        b.applyMove(mv[0], mv[1], side);
      }
    }
    // Land on a searchable node: if the side to move must pass, advance past it.
    while (!b.isGameOver() && b.legalMoves(b.getSideToMove()).isEmpty()) {
      b.applyMove(0, Board.PASS, b.getSideToMove());
    }
    return b;
  }
}