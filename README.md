# Connect Four (Android)

A small Android app that plays Connect Four against you, using the same
minimax logic as your Python script — ported to Kotlin.

## Project layout

```
app/src/main/java/com/example/connectfour/
├── engine/
│   ├── ConnectFourEngine.kt   # Board state & rules only: moves, win/draw
│   │                          # detection, line scoring. No "AI" here.
│   ├── GameAlgorithm.kt       # Interface every move-choosing strategy implements.
│   └── MinimaxAlgorithm.kt    # Direct port of BestColumn()/Evaluate() from
│                              # your Python code. Implements GameAlgorithm.
└── MainActivity.kt            # Jetpack Compose UI: draws the board, handles
                                # taps, calls the engine + algorithm.
```

The split mirrors your request: `ConnectFourEngine` only knows the *rules*
of Connect Four (board, legal moves, win detection, the line-based scoring
table). It has no idea how moves get chosen. `MinimaxAlgorithm` is the only
place that does lookahead/search, and it only touches the engine through
its public methods (`doMove`, `undoMove`, `gameScore`, `gameWon`, etc.).

## Adding a new algorithm later

Implement the interface:

```kotlin
class NeuralNetAlgorithm(/* model, weights, whatever */) : GameAlgorithm {
    override fun bestColumn(engine: ConnectFourEngine, player: Int, searchDepth: Int): Int {
        // Read the board via engine.counterValue(col, row), run inference,
        // return the chosen column.
    }
}
```

Then swap it in where `MainActivity.kt` creates the algorithm:

```kotlin
fun ConnectFourScreen(algorithm: GameAlgorithm = remember { NeuralNetAlgorithm() })
```

Nothing else needs to change — `ConnectFourEngine` and the UI don't care
which `GameAlgorithm` is plugged in.

## Building it

This is a standard Gradle Android project, but it doesn't include the
Gradle wrapper binaries (they're not text files, so they're omitted here).
Easiest path:

1. Open Android Studio → **Open** → select this `ConnectFour` folder.
2. Android Studio will detect it's missing a wrapper and offer to create
   one (or just let it sync — it uses its bundled Gradle automatically).
3. Let Gradle sync finish (first sync will download dependencies, so you
   need an internet connection for that step).
4. Run ▶ on an emulator or device (minSdk 24 / Android 7.0+).

If you'd rather use the command line, install Gradle (or generate a
wrapper with `gradle wrapper` once you have any Gradle available) and
then run `./gradlew installDebug` with a device/emulator connected.

## Notes on the port

- The board/line bookkeeping (`ConnectFourEngine`) and the search
  (`MinimaxAlgorithm.bestColumn` / `evaluate`) match your Python
  line-for-line, including the fail-soft alpha/beta-style cutoff and the
  reservoir-sampling tie-break for equally-scored moves.
- `SEARCH_DEPTH` in `MainActivity.kt` (currently 5) controls how many plies
  the computer looks ahead — matches `iLevel + 4` in your `play_game()`
  with level 1. Raise it for a stronger but slower computer player.
- The computer's search runs on a background dispatcher
  (`Dispatchers.Default`) so it doesn't freeze the UI thread while thinking.
