# BrainExporter Agent Rules

Before any UI redesign work:

1. Read `PRODUCT.md`, `DESIGN.md`, and `docs/PLANET_UI_REDESIGN_AGENT.md` completely.
2. Run `bash scripts/setup-agent-skills.sh codex` once on the development machine.
3. For visual/UX decisions, explicitly use the `ui-ux-pro-max` skill.
4. For Jetpack Compose implementation/review, explicitly use the `modern-jetpack-compose` skill.

## Non-negotiable product constraints

- UI/UX may be completely redesigned, but existing product capabilities and data behavior must not regress.
- Keep Kotlin + Jetpack Compose + Material 3 and the current modular architecture. Do not replace the stack.
- Do not move EEG data to cloud storage, add analytics, or add hidden network behavior.
- Keep the app dark-only. Do not enable Dynamic Color or add a light theme.
- Green/teal must not be a brand/primary color. A restrained success/connected status color is allowed only for tiny semantic indicators.
- Preserve Chinese UI copy unless a wording change is clearly needed for hierarchy or clarity.

## Quality gate

Before pushing a redesign branch, run:

```bash
./gradlew --no-daemon test lint assembleDebug
```

After opening/updating the PR, inspect GitHub Actions and fix failures yourself. Use `gh run watch` and `gh run view --log-failed` when available. Do not hand a red CI run back to the user.

## Release / history policy

- `v0.3.0` already exists as a published GitHub Release and tag. Never delete, move, or recreate it.
- The next redesign release target is `v0.4.0` / `versionCode = 4` unless the user explicitly chooses another unused version.
- The final redesign PR must be exactly one commit ahead of its base before merge. Work locally without committing, or squash/reset before the final push.
- Merge the final redesign PR with squash. Tag the resulting main commit only after CI is green; the tag triggers the release workflow.
- Do not create a tag or GitHub Release until all release gates in `docs/PLANET_UI_REDESIGN_AGENT.md` pass.
