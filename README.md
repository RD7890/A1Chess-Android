# A1 Chess — Native Kotlin Android App

Native Android chess app built with Jetpack Compose, replicating A1Chess (RDChess) in pure Kotlin.

## Features
- Play vs Stockfish (Level 1–12)
- Over the Board (2 players, 1 device)
- Green board + Staunty pieces (Lichess assets)
- Real-time move tracking panel
- Eval bar
- Arrows on board
- Engine settings (search time, MultiPV, CPUs)
- Dark theme matching original app

## Setup

### 1. Add Stockfish Binary
The app needs a Stockfish binary for Android ARM64. Download from:
- https://github.com/official-stockfish/Stockfish/releases
- Or copy the ARM64 binary from your existing build

Place the binary at:
```
app/src/main/assets/stockfish
```
Make sure it's the ARM64-v8a build for Android.

## Stack
- Kotlin + Jetpack Compose
- `chesslib` — chess rules/logic
- `androidsvg` — Staunty SVG piece rendering
- `datastore-preferences` — persistent settings
- Stockfish 16 via stdin/stdout UCI

## Project structure
```
app/src/main/java/com/ryzix/rdchess/
├── MainActivity.kt
├── chess/
│   ├── ChessGame.kt         ← chess logic (chesslib wrapper)
│   ├── StockfishEngine.kt   ← UCI engine communication
│   └── SoundManager.kt      ← move sounds
├── viewmodel/
│   └── GameViewModel.kt     ← game + engine state
└── ui/
    ├── theme/               ← dark theme, colors
    ├── navigation/          ← nav graph
    └── screens/
        ├── HomeScreen.kt
        ├── GameScreen.kt
        ├── SettingsScreen.kt
        ├── ThemeSettingsScreen.kt
        └── components/
            ├── ChessBoard.kt    ← board + pieces + arrows
            ├── MovePanel.kt     ← move list + eval bar
            └── GameBottomBar.kt ← undo/flip/new/settings
```
