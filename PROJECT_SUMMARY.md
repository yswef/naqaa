# Project summary (to continue in another conversation or with another model)

## Rules given by the owner (verbatim intent)
- Return only modified files / modified parts, with path and place.
- No guessing: if unsure, stop, write a diagnostic function, ask for logs.
- Document every significant change in a .md file.
- Git commits: realistic, varied dates and times.
- Keep replies short; say what was done and where.

## Important information
- App: "Naqaa" (نقاء), Android, Kotlin + Jetpack Compose, package `com.naqaa.app`, minSdk 26, target 36.
- Repo: github.com/yswef/naqaa, branch `main`. CI: `.github/workflows/build.yml`
  (tests, release APK, signing from NAQAA_* secrets, release `v<versionName>`).
- No Android SDK in the assistant's sandbox: nothing was compiled locally; Kotlin syntax was
  checked with kotlinc only. First CI run is the real compile.
- Version now 1.1.0 (code 11); details in `docs/CHANGES_1.1.0.md` and `CHANGELOG.md`.

## Full details
- Strings must exist in both `values/` and `values-en/`; `tools/check_strings.py` runs in CI.
- Failures are written to `last-error.txt`; archived as `previous-error.txt` at next start.
- Known limit: a PIN longer than 6 digits saved by 1.0.9 or older cannot be entered.

## Code style
- Kotlin official style, 4 spaces, KDoc comments explaining why, no extra dependencies,
  state through `UiState`/`AppViewModel`, small composables in `ui/screens`.
