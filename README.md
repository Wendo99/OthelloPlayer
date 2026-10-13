# OthelloPlayer

A tournament Othello (Reversi) engine written in Java for the *OthelloWettkampf* arena, a
university competition framework, provided by the course professor, in which student-written players compete on an 8×8 board
under a fixed total thinking time of 120 seconds.

The engine maintains its own board representation independent of the framework. On each turn,
it runs a time-bounded NegaScout search and returns the best move it managed to prove before the
clock runs out.

## Results and reflection

**Tournament Outcome:**
The engine was built for the university's *Intelligente Systeme* Othello tournament, where it
finished in **4th place**.

The following assessment is a hypothesis, not a diagnosis as this would require the tournament games
statistics. Search
optimization did indeed provide more depth at the expense of speed, but the manually weighted
evaluation likely was not sufficiently fine-tuned. The most promising levers would be,
for example, a more carefully calibrated or learned evaluation and an endgame solved precisely
at an early stage. Only an analysis of the tournament games, these are not available, could reveal
which of these factors is actually decisive.

**Technical Benchmarks:**
Running on a modern JVM (Java 21), the engine achieves a stable throughput of **600 – 800 kNPS**
(kilo-nodes per second). Using killer moves alone reduced the midgame node count at depth 10 by
roughly **37%** (from 2.27M down to 1.43M nodes).

## Architecture and components

The arena only exchanges single moves. The full position, ruleset, and search tree are
reconstructed and maintained internally following the Single-Responsibility Principle:

| Component                                                     | Role                                                                                                                                               |
|:--------------------------------------------------------------|:---------------------------------------------------------------------------------------------------------------------------------------------------|
| [`Spieler`](src/main/java/spieler/wendo/Spieler.java)         | **Adapter:** Translates framework `Zug` objects, replays opponent moves, delegates searches, and detects state desyncs.                            |
| [`Board`](src/main/java/spieler/wendo/Board.java)             | **State & Rules:** Encapsulates rule logic, legal-move generation, flipping, and state checks.                                                     |
| [`MiniMax`](src/main/java/spieler/wendo/MiniMax.java)         | **Search:** NegaScout (alpha-beta variant) with iterative deepening, a killer-move heuristic, and static move ordering.                            |
| [`Evaluate`](src/main/java/spieler/wendo/Evaluate.java)       | **Heuristics:** Phase-aware static evaluation. Midgame prioritizes corners, mobility, and positional weights; endgame targets raw disc difference. |
| [`TimeManager`](src/main/java/spieler/wendo/TimeManager.java) | **Clock:** Splits the game clock dynamically (remaining time ÷ expected own moves left) and holds a 10 % safety reserve per move.                  |
|

## Board representation and copy-make

The 8×8 board is represented as a flat `int[64]` array indexed via `row * 8 + col` (with row 0, col
0 at the top left).

The search relies on the **copy-make pattern**: for every candidate move, `copy()` creates an
independent board instance via `System.arraycopy`, keeping the original state untouched. Using a
primitive flat array ensures that these millions of allocations remain extremely cheap on the JVM.

## Search and evaluation strategy

- **NegaScout & Iterative Deepening:** Formulated in Negamax. The first move of the ordered list is
  searched with a full window; subsequent moves are tested with a cheap null-window probe (scout),
  triggering a full re-search only upon a fail-high. Iterative deepening (depth 1, 2, 3, …) ensures
  a valid move is always ready when the clock expires.
- **Three-Tier Move Ordering:** To maximize alpha-beta pruning efficiency, moves are ordered via:
    1. Static square weights (corners highly rewarded, X/C-squares heavily penalized).
    2. Killer moves (storing up to two beta-cutoff-triggering moves per depth).
    3. Principal variation (re-evaluating the best move from the previous iterative pass first).
- **Phase-Aware Evaluation:**
    - *Midgame (< 50 discs):* Blends corner control, normalized mobility, and positional weights.
      Raw disc count is deliberately ignored because early stone advantages restrict mobility.
    - *Endgame ($\ge$ 50 discs):* Switches to disc difference while keeping corner weights high.

## Setup, build, test, and run

**Requirements:**

- JDK 21
- Maven 3.9+

The arena framework is pre-installed in [`repo/`](repo/) as a file-based Maven repository. No manual
installation is needed.

**Build and test:**

```bash
mvn test
mvn package
```

The JUnit 5 test suite covers the rules engine, phase evaluation, search invariants, time
management, and the framework adapter contract.
Running a match:
OthelloWettkampf runs a local test match. By default, it pits this engine against a shipped
reference opponent (Rumpfspieler) at depth 7.

```bash
mvn -q exec:java

```

The match protocol, provided by the Framework, is written to log/Othello.log relative to the working directory.

## Limitations

The board is represented as an array (`int[64]`) rather than 64-bit Bitboards. Move generation
relies on array traversal and a copy-make approach rather than bitwise shifts and make-undo.

## Provenance and licensing

The code I wrote is located under src/main/java/spieler/wendo/ and src/test/java/spieler/wendo/. It
is covered by the MIT LICENSE in this repository.
Two parts are university course material and are not covered by that license:

- `src/main/java/OthelloWettkampf.java`: A provided template to run local test matches.
- `repo/de/wendo/OthelloWettkampf/1.0.0/`: The proprietary arena framework (rahmen and spieler
  packages), vendored here solely for the build process.
  Rights to both remain with the author of the course material.
