# A1 Chess Android — Full Project Context

## What this project is
A native Android chess app written in **Kotlin + Jetpack Compose**.  
- App name: **A1 Chess**  
- Package: `com.ryzix.rdchess`  
- GitHub repo: `https://github.com/RD7890/A1Chess-Android`  
- GitHub user: **RD7890**  
- PAT scope: full repo + workflow  

## Features
- Green board with Staunty SVG pieces (assets in `app/src/main/assets/pieces/staunty/`)
- Evaluation bar (white/black fraction bar + centipawn display)
- Stockfish 10 engine (ARM64 binary compiled from source in CI)
- Sound effects: move, capture, confirmation, error (MP3 in `app/src/main/assets/sounds/`)
- Navigation: Home → Game / Settings → ThemeSettings
- DataStore preferences: engine level, search time, MultiPV, threads, play-as-white, show-arrows

## Stack
| Layer | Library/Tool |
|---|---|
| Language | Kotlin 2.0.0 |
| UI | Jetpack Compose (BOM 2024.08.00 → Material3 1.2.1) |
| Navigation | androidx.navigation:navigation-compose 2.7.7 |
| Chess logic | com.github.bhlangonijr:chesslib 1.3.4 (JitPack) |
| SVG pieces | com.caverock:androidsvg-aar 1.4 |
| Engine | Stockfish 10 (compiled ARM64 from source in CI) |
| Sound | android.media.SoundPool (no extra lib) |
| Preferences | androidx.datastore:datastore-preferences 1.1.1 |
| Build | AGP 8.5.2, Gradle 8.7, JDK 17 |
| compileSdk | 35, minSdk 26, targetSdk 35 |

## File map
```
A1Chess-Android/
├── .github/workflows/build.yml        ← CI: compile SF10 + assemble APK + Telegram notify
├── app/
│   ├── build.gradle.kts               ← AGP config, signing, dependencies
│   ├── proguard-rules.pro             ← keep chesslib + androidsvg
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/
│       │   ├── pieces/staunty/*.svg   ← wK wQ wR wB wN wP bK bQ bR bB bN bP
│       │   ├── sounds/*.mp3
│       │   └── stockfish              ← ARM64 binary (produced by CI, NOT committed)
│       ├── java/com/ryzix/rdchess/
│       │   ├── MainActivity.kt
│       │   ├── chess/
│       │   │   ├── ChessGame.kt       ← board state, moves, FEN, chesslib wrapper
│       │   │   ├── StockfishEngine.kt ← process I/O, UCI, eval/bestmove flows
│       │   │   └── SoundManager.kt    ← SoundPool wrapper
│       │   ├── viewmodel/
│       │   │   └── GameViewModel.kt   ← AndroidViewModel, DataStore prefs, engine bridge
│       │   └── ui/
│       │       ├── navigation/AppNavigation.kt
│       │       ├── screens/
│       │       │   ├── HomeScreen.kt
│       │       │   ├── GameScreen.kt          ← board + eval bar + move panel + dialogs
│       │       │   ├── SettingsScreen.kt
│       │       │   ├── ThemeSettingsScreen.kt
│       │       │   └── components/
│       │       │       ├── ChessBoard.kt      ← Canvas drawing, SVG pieces, arrows
│       │       │       ├── MovePanel.kt       ← eval bar + move list
│       │       │       └── GameBottomBar.kt
│       │       └── theme/
│       │           ├── Color.kt
│       │           ├── Theme.kt
│       │           └── Type.kt
│       └── res/
│           ├── values/strings.xml
│           └── values/themes.xml      ← parent: Theme.AppCompat.DayNight.NoActionBar
├── gradle/libs.versions.toml          ← version catalog
├── gradle.properties                  ← android.useAndroidX=true, suppressUnsupportedCompileSdk=35
├── settings.gradle.kts                ← includes JitPack repo (for chesslib)
└── context.md                         ← this file
```

## CI / GitHub Actions (`build.yml`)
The workflow triggers on push to `main`/`dev`/`release/**`.

### Steps in order
1. Cancel duplicate runs
2. Telegram: "Build Started" notification
3. Checkout
4. Setup JDK 17 (temurin)
5. Setup NDK r25c
6. Restore Gradle cache
7. **Restore Stockfish cache** — key: `stockfish-10-arm64-ndk-r25c-v3`
8. **Build Stockfish 10 ARM64** (if cache miss)
   - Downloads `sf_10.tar.gz` from official-stockfish
   - Patches Makefile: `sed -i 's/^build: config-sanity/build:/' Makefile` ← CRITICAL: skips sanity check that tries to run ARM64 binary on x86 host
   - Compiles with `aarch64-linux-android21-clang++` from NDK r25c
   - Output: 956 056 bytes ARM64 binary → `app/src/main/assets/stockfish`
9. Verify Stockfish binary exists
10. Generate signing keystore (alias: `A1Chess`, password: `123456`)
11. Write `local.properties` with signing config
12. `chmod +x gradlew`
13. **`./gradlew :app:assembleRelease`** — output logged to `build.log`
14. Get version name from `build.gradle.kts`
15. Rename APK to `A1Chess-vX.X-bN.apk`
16. Upload APK as GitHub Actions artifact (30-day retention)
17. Create GitHub Release (on `main` / `release/**` only)
18. Telegram: SUCCESS — sends message + last 200 lines of `build.log` as document
19. Telegram: FAILURE (if any step failed) — sends message + `fail_log.txt`

### Telegram secrets (set in repo Settings → Secrets)
| Secret | Value |
|---|---|
| `TELEGRAM_BOT_TOKEN` | `8754203819:AAFT2vvTR4iajt_uDQdvj4RSkHqDsrsd8f0` |
| `TELEGRAM_CHAT_ID` | `6759785872` (username: modsformcpe0077) |

## Bugs fixed in CI history
| Build | Problem | Fix |
|---|---|---|
| #1 | SF10 `config-sanity` ran ARM64 binary on x86 CI host → SIGILL | `sed -i 's/^build: config-sanity/build:/'` |
| #1 | Commit SHA was printed literally as `$(echo ... \| cut)` | Store in `SHORT_SHA` var first |
| #2 | `android.useAndroidX` not set → `checkReleaseAarMetadata` failed | Added `gradle.properties` |
| #3 | `android:style/Theme.Material.NoTitleBar not found` on API 35 | Changed parent to `Theme.AppCompat.DayNight.NoActionBar` in `themes.xml` |
| #4 | `Theme.AppCompat.DayNight.NoActionBar not found` — appcompat not in deps | Changed parent to `android:Theme.Material.Light` + `windowNoTitle=true` + `windowActionBar=false` (platform theme, no extra dep needed) |

## Known gotchas
- `stockfish` binary is NOT committed to the repo — it is built in CI and cached. If you change the NDK version or anything about the build, bump the cache key suffix (`v3` → `v4`).
- `chesslib 1.3.4` is from JitPack (`https://jitpack.io`). The `settings.gradle.kts` must include JitPack in `dependencyResolutionManagement.repositories` or it won't resolve.
- `android.suppressUnsupportedCompileSdk=35` is in `gradle.properties` to silence the AGP 8.5.2 / compileSdk 35 warning (non-fatal).
- `Divider()` in Material3 is deprecated (use `HorizontalDivider`) — currently used in GameScreen, SettingsScreen, ThemeSettingsScreen. It compiles fine with a warning.
- The app theme parent MUST be an AppCompat/Material theme (not `android:Theme.Material.*`) because `activity-compose` and edge-to-edge API depend on AppCompat.
- Signing keystore is generated fresh every CI run (not stored as a secret). APKs signed this way are NOT suitable for Play Store upload — each build has a different key. For Play Store, store the keystore as a base64 secret.

## Next things to do (not yet built)
- Add game history / replay screen
- Play as Black option on GameScreen (currently hardcoded to White)
- Store persistent signing keystore as GitHub secret for Play Store builds
