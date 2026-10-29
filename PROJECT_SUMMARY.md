# Project summary (to continue in another conversation or with another model)

## Rules given by the owner (verbatim intent)
- Return only modified files / modified parts, with path and place.
- No guessing or assuming; if unsure or the location of a problem is uncertain, do not act: write a diagnostic (bash/PowerShell script or in-app report with an output file) and ask for the logs.
- Do not modify existing logic when finding bugs; present proposals at the end of the reply.
- If unsure or ambiguous, ask before proceeding.
- Keep replies short: what was done and in which function/file; explain only when asked.
- Do not review earlier messages in the conversation and do not reference other conversations; important info lives in this file.
- With each reply maintain this summary (rules verbatim, important info, full details, code style).
- Minimise token use; if usage is high give a detailed summary to move to another conversation.
- Document every significant change in a .md file. Git commits: realistic, varied dates and times.

## Important information
- App "Naqaa" (نقاء), Android, Kotlin + Compose, `com.naqaa.app`, minSdk 26, target 36. Repo github.com/yswef/naqaa, CI `.github/workflows/build.yml`.
- Version 1.2.0 (code 13); details `docs/CHANGES_1.2.0.md`. Nothing compiled locally; first CI run is the real compile.
- Update conflict cause: CI throw-away key when `NAQAA_*` secrets are missing; fix = `tools/make_keystore.sh` once.
- Open: audio files must be supplied (`assets/sounds/`); Instagram clean-up labels untested (needs screen report); VPN block needs a VPN report if Windscribe still works; accessibility on Oppo/ColorOS still pending from 1.1.1.

## Full details
- Strings in both `values/` and `values-en/`; `tools/check_strings.py` in CI.
- Hidden developer credentials are not in the repo or docs (only hashes in `DevAccount`); `tools/make_dev_hash.py` regenerates.
- Prayer record: kind PRAYER, trigger = prayer key, note ontime/late. Plan tasks: `planTasks` keys `day:b|s|c`.
- Passes are memory only (`UsagePass`); backups are format 1 (`Backup`).

## Code style
- Kotlin official style, 4 spaces, KDoc explaining why, no extra dependencies, state through `UiState`/`AppViewModel`, small composables in `ui/screens`.
