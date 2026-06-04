# Agent Rules — A1 Chess Android

Read this before touching anything. These rules exist because of real failures that happened.

---

## 1. Never guess — read `context.md` first

`context.md` in this repo root has the full picture:
- Every file and what it does
- Every CI bug that was fixed and why
- All library versions and gotchas

If you have not read `context.md`, read it before making any change.

---

## 2. GitHub — how to push files

Use the **GitHub Contents API**, not git commands.  
PAT is stored in Replit secrets (never hardcode it in code).

```
GET  /repos/RD7890/A1Chess-Android/contents/<path>   → get current SHA
PUT  /repos/RD7890/A1Chess-Android/contents/<path>   → update (needs SHA) or create (no SHA)
```

Always GET the file first to get its SHA before a PUT update, or the API returns 422.  
For new files (don't exist yet), PUT without `sha`.

---

## 3. CI — the Stockfish cache key rule

Current key: `stockfish-10-arm64-ndk-r25c-v3`

**Bump the version suffix (`v3` → `v4` etc.) whenever:**
- NDK version changes
- Makefile patch changes
- Compile flags change
- A previous run cached a broken/empty binary

A stale cache with a bad binary will silently fail at runtime. Bump the key to force a fresh compile.

---

## 4. The Makefile patch is NOT optional

SF10's Makefile has `build: config-sanity`. The `config-sanity` rule runs the compiled ARM64 binary on the x86 CI host to verify it — this crashes with SIGILL on cross-compilation.

The patch **must** be applied before `make`:
```bash
sed -i 's/^build: config-sanity/build:/' Makefile
```

Do NOT remove this line. Do NOT "clean up" the workflow thinking it's unnecessary.

---

## 5. `gradle.properties` must have these lines

```properties
android.useAndroidX=true
android.enableJetifier=true
android.suppressUnsupportedCompileSdk=35
```

Without `android.useAndroidX=true`, the build fails at `checkReleaseAarMetadata` because AndroidX dependencies are present but the flag is off.

---

## 6. The app theme parent — NEVER use `android:Theme.Material.*`

`themes.xml` parent must be:
```xml
parent="Theme.AppCompat.DayNight.NoActionBar"
```

`android:Theme.Material.NoTitleBar` does NOT exist on `compileSdk=35` (AAPT error: resource not found).  
AppCompat is available transitively via `activity-compose`.

---

## 7. Edit files — never rewrite from scratch

Use targeted edits. Read the file first, then change only what needs changing.  
Rewriting loses context and introduces new bugs.

---

## 8. `stockfish` binary is NOT in the repo

The binary at `app/src/main/assets/stockfish` is produced by CI and cached.  
Do not try to commit it. Do not treat its absence locally as a bug.

---

## 9. `chesslib` comes from JitPack

`settings.gradle.kts` must have JitPack in `dependencyResolutionManagement.repositories`:
```kotlin
maven { url = uri("https://jitpack.io") }
```

Without this, Gradle cannot resolve `com.github.bhlangonijr:chesslib:1.3.4`.

---

## 10. Signing is ephemeral — not Play Store ready

The CI generates a fresh keystore every build with:
- alias: `A1Chess`
- passwords: `123456`

APKs are sideload-only. For Play Store, a persistent keystore must be stored as a base64 GitHub secret.

---

## 11. Telegram notification format

Both SHA variables must be assigned to a shell variable first:
```bash
SHORT_SHA=$(echo "${{ github.sha }}" | cut -c1-7)
```

Never use `$(echo ${{ github.sha }} | cut -c1-7)` inside a `printf` argument — GitHub Actions expands `${{ }}` at parse time but the `$(...)` subshell does not inherit it properly inside nested quotes, resulting in the literal string being printed.

---

## 12. When a build fails — what to check in order

1. Read the FAILED STEP name from GitHub Actions
2. Look at the error line — almost always one clear message
3. Match it against the bug history table in `context.md`
4. If it's a new error, fix ONLY that error — do not rewrite unrelated files
5. Push the fix, update `context.md` bug history table with the new entry

---

## 13. Update `context.md` after every fix

After every bug fix or meaningful change, update:
- The **Bug history** table in `context.md`
- The **Gotchas** section if a new sharp edge was found
- The **File map** if new files were added

This keeps the loop from repeating.
