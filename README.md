# OthelloPlayer

A tournament Othello (Reversi) engine written in Java for the *OthelloWettkampf* arena, a
university competition framework in which student-written players play each other on an 8×8 board
under a fixed total thinking time.

The player itself is `spieler.wendo.Spieler`. It implements the framework's `OthelloSpieler`
interface. The arena hands it the opponent's last move and the clocks, and it answers with its own
move.

## What it does

The engine keeps its own board and rules implementation, independent of the framework. The arena
only ever exchanges single moves, so the full position is reconstructed and maintained internally.
On each turn it runs a time-bounded search and returns the best move it managed to prove.

| Class | Role                                                                                                                                       |
| --- |--------------------------------------------------------------------------------------------------------------------------------------------|
| [`Spieler`](src/main/java/spieler/wendo/Spieler.java) | Adapter between arena and engine: translates `Farbe`/`Zug`, replays the opponent's move, delegates the search, detects state desync        |
| [`Board`](src/main/java/spieler/wendo/Board.java) | Game state and rules: a flat `int[64]` indexed `row * 8 + col`, with legal-move generation, flipping, passes and copying                   |
| [`MiniMax`](src/main/java/spieler/wendo/MiniMax.java) | NegaScout (alpha-beta variant) with iterative deepening, killer-move heuristic and static move ordering                                    |
| [`Evaluate`](src/main/java/spieler/wendo/Evaluate.java) | Phase-aware static evaluation: corners, mobility and a positional weight table in the midgame; disc difference plus corners in the endgame |
| [`TimeManager`](src/main/java/spieler/wendo/TimeManager.java) | Splits the game clock into a per-move budget (remaining time ÷ expected own moves left, minus a 10 % reserve)                              |

Search notes worth knowing:

- Iterative deepening searches depth 1, 2, 3, … and keeps the best move from the last depth
  that completed. Running out of time mid-depth is normal and safe — the previous result stands.
- Time is the real limit, not depth. The configured depth is only an upper bound; the player is
  usually configured with depth 100 so that only the clock stops it.
- The maximum depth is additionally capped at the number of empty squares, so an endgame search can
  become exact.
- A pass does not consume a ply, it only swaps the side to move.

## Requirements

- JDK 21 (the build targets release 21)
- Maven 3.9+

The arena framework is not on Maven Central. It ships with this repository as a pre-installed
artifact in [`repo/`](repo/), which `pom.xml` declares as a file-based repository. No manual
install step is needed.

## Build and test

```bash
mvn test
```

```bash
mvn package
```

The test suite (JUnit 5) covers the rules engine, the evaluation function, search behaviour, the
time manager and the framework adapter contract.

## Running a match

[`OthelloWettkampf`](src/main/java/OthelloWettkampf.java) is the entry point. It builds a player
list, hands it to `rahmen.OthelloArena` and the match starts:

```java
List<String> spieler = new ArrayList<>();
spieler.add("spieler.wendo.Spieler:100");   // this engine, depth cap 100
spieler.add("spieler.Rumpfspieler:7");      // reference opponent shipped with the framework

new OthelloArena(150, spieler, protokolldatei, true);
```

Each entry is `fully.qualified.ClassName:depth`. The first `OthelloArena` argument is the total
thinking time per player in seconds, the third is the log file path, and the fourth enables debug
output.

Run it with:

```bash
mvn -q exec:java
```

The match protocol is written to `log/Othello.log` relative to the working directory, the directory
is created if it does not exist.

Alternatively, run `OthelloWettkampf` directly from the IDE, or from the packaged jar together with
the framework jar on the classpath.

## Provenance and licensing

The code I wrote is everything under [`src/main/java/spieler/wendo/`](src/main/java/spieler/wendo/)
and [`src/test/java/spieler/wendo/`](src/test/java/spieler/wendo/). That
code is covered by the MIT [`LICENSE`](LICENSE) in this repository.

Two parts are course material, not my work, and not covered by that license:

- [`src/main/java/OthelloWettkampf.java`](src/main/java/OthelloWettkampf.java): a template
  provided with the course to run local test matches. My only changes are the player list and the
  log-file path handling; see the class Javadoc.
- [`repo/de/wendo/OthelloWettkampf/1.0.0/`](repo/de/wendo/OthelloWettkampf/1.0.0/): the arena
  framework (`rahmen` and `spieler` packages), vendored here only so the project builds.

Rights to both remain with the author of the course material. The competition between all
participants' players was run separately on the course's own framework. The harness here was purely
a local development tool.

## Benchmarking

[`NodeBenchmark`](src/test/java/spieler/wendo/NodeBenchmark.java) measures nodes searched at fixed
depth from the opening and from a deterministic midgame position. It is deterministic, so the node
counts of two code versions are directly comparable. This is useful when judging whether a change to move
ordering or evaluation actually helps.
