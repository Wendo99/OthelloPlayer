# OthelloPlayer — NegaScout Game-Playing Engine

A Java Othello (Reversi) engine developed for a university tournament in the Intelligente Systeme course. The engine maintains an internal board, uses NegaScout search with iterative deepening, and allocates a finite thinking-time budget across moves. It finished 4th place in the course tournament.

The engine integrates with the university-provided OthelloWettkampf arena through a small adapter; the core board, evaluation, search and clock logic are implemented separately.

## Quick start

Requirements: JDK 21 and Maven 3.9 or newer.

```zsh
git clone https://github.com/Wendo99/OthelloPlayer.git
cd OthelloPlayer
mvn test
mvn package
mvn -q exec:java
```

## Architecture and components

```text
University Othello arena (Zug/Farbe interface)
                     |
                     v
              Spieler adapter
        convert and validate moves
                     |
          +----------+-----------+
          |                      |
          v                      v
        Board                TimeManager
    game state/rules       per-move budget
          |                      |
          +-----------+----------+
                      v
                   MiniMax
       iterative-deepening NegaScout
                      |
                      v
                   Evaluate
          phase-aware board score
```

| Component | Responsibility |
| --- | --- |
|[`Spieler`](src/main/java/spieler/wendo/Spieler.java)	| Implements the arena interface; replays previous moves, checks state consistency and returns legal Zug objects |
|[`Board`](src/main/java/spieler/wendo/Board.java) |	Encapsulates the 8×8 game state, legal moves, flipping, passes and game-over rules|
|[`MiniMax`](src/main/java/spieler/wendo/MiniMax.java)|	Runs iterative-deepening NegaScout with alpha–beta windows, ordering heuristics and deadline checks|
|[`Evaluate`](src/main/java/spieler/wendo/Evaluate.java) |	Assigns phase-aware position scores using positional and mobility features|
|[`TimeManager`](src/main/java/spieler/wendo/TimeManager.java) |	Computes per-move thinking-time budgets from elapsed time and remaining empty squares|
|[`OthelloWettkampf`](src/main/java/OthelloWettkampf.java) |	Course-provided local demonstration driver with a small amount of project-specific configuration|

The internal board is a flat int[64] array indexed by row * 8 + column. Each candidate move is evaluated on a copy of the position (System.arraycopy), preventing branches of the search tree from mutating one another. The framework adapter keeps the arena-facing protocol separate from search and game logic.

## Search, time management and safety checks

- NegaScout: Searches the first ordered move with a full alpha–beta window, probes later moves with a narrow window and re-searches when necessary.
- Iterative deepening: Deepens the search incrementally so that a move from the most recently completed iteration can be retained when the time budget expires.
- Move ordering: Uses static square values, killer moves and priority for the previously identified best line to improve pruning.
- Phase-aware evaluation: Uses mobility, corners and positional weights in the midgame, then emphasizes disc difference toward the endgame (threshold: 50 occupied squares).
- Clock management: Estimates remaining turns and assigns a per-move budget with a 10% reserve; the implementation enforces a minimum 50 ms budget. Timing is a best-effort heuristic, not a hard real-time guarantee.
- State validation: The adapter checks incoming moves and passes against its internal board and raises a ZugException on detected inconsistencies rather than silently proceeding with a desynchronized state.

## Testing and validation
The project uses JUnit 5 and Maven. Run its tests with:

```zsh
mvn test
```

The suite includes tests of move legality, flipping and board state, phase-dependent evaluation, search behavior, time-budget calculations, and the arena adapter (including illegal opponent moves and passes when a legal move exists).

To run the local demonstration match and inspect its generated protocol:

```zsh
mvn -q exec:java
# Then inspect log/Othello.log
```

The repository also contains [`NodeBenchmark.java`](src/test/java/spieler/wendo/NodeBenchmark.java), a separate benchmark main program for deterministic fixed-depth node-count and timing measurements. It is not a JUnit test and does not run automatically with mvn test. Reproducing benchmark timing requires documenting the processor, JDK, JVM settings and measurement procedure; node counts are more suitable for comparing search variants when the benchmark position and depth are fixed.

## Technical benchmark

Search benchmark: In a depth-10 search of one deterministically generated midgame position, a revised search implementation reduced the recorded node count from 2,265,675 to 1,434,016 (36.7%; commits c7a05d9 → 11785ba). The revision included killer-move ordering and changes to terminal-position handling, so the improvement cannot be attributed to either change in isolation. The result concerns one position and does not establish an equivalent reduction in execution time.

The search improvements increase the amount of tree exploration that can be attempted within a time budget, but their effect on tournament results was not isolated. A plausible further improvement would be a better calibrated evaluation function or more specialized endgame search; determining the actual bottleneck requires tournament game records.

## Project structure

```text
src/main/java/spieler/wendo/      # Author's engine and arena adapter
src/main/java/OthelloWettkampf.java  # Course-provided local driver
src/test/java/spieler/wendo/      # JUnit tests and NodeBenchmark
src/test/resources/               # Test logging configuration
repo/                             # Locally bundled course framework artifact
pom.xml                           # Dependencies, Java version, build and exec settings
```

## Limitations and future work
- Board representation: An int[64] array and copy-per-move scheme are straightforward to reason about, but 64-bit bitboards and make/undo move techniques may be faster. No directly comparable implementation is benchmarked here.
- Search strategy: Manual phase-aware weights may underperform a learned or systematically tuned evaluator; a more exact endgame solver is another potential extension.
- Timing: Deadline checks occur periodically during node exploration, so execution may overshoot a target budget. The 10% reserve reduces risk but does not guarantee timing compliance.
- Performance evidence: The reported killer-move result covers a single deterministic position. More positions, repeated timings and captured environment information would be needed for general performance claims.
- Tournament analysis: Without full game logs, conclusions about why the engine placed fourth remain hypotheses.

##  Provenance and licensing
  The author's implementation is in [`src/main/java/spieler/wendo/`](src/main/java/spieler/wendo/) and [`src/test/java/spieler/wendo/`](src/test/java/spieler/wendo/). That code is covered by the repository's MIT license.

Two bundled components are university course material and not covered by that MIT license:
- [`src/main/java/OthelloWettkampf.java`](src/main/java/OthelloWettkampf.java) — a provided local-test driver, with minor project-specific changes.
- [`repo/de/wendo/OthelloWettkampf/1.0.0/`](repo/de/wendo/OthelloWettkampf/1.0.0/) — the course's proprietary arena framework artifact and accompanying repository files.

Redistribution permission has not been established by this README.