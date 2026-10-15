# Contributing to SynapseOS

SynapseOS is maintained as a small, testable Android project plus device and Home Assistant tooling. Contributions are welcome, but the public repository and physical-device boundaries are strict.

## Before editing

1. Pull the current repository state and inspect `coord/STATUS.md` and the newest `coord/REQUESTS.md` entries.
2. Read [`coord/PROTOCOL.md`](coord/PROTOCOL.md), [`SECURITY.md`](SECURITY.md), and the relevant architecture/data-contract document.
3. Identify the owner of the files you need. Edit only your own status section; leave requests or findings for other contributors.
4. Claim the work in `coord/STATUS.md` if it is a multi-step feature, and add a request when you need HANDS or another agent to do something.

## Branches and commits

- `main` is the shared integration branch. The frozen `claude/*` branches are historical and must not receive new work.
- Use a focused branch such as `manus/<feature>` or the branch convention agreed in the current coordination board.
- Pull/rebase before beginning; never force-push.
- Keep commits small and explain the user-visible behavior in the subject. Include the request ID when one exists, for example: `R-124 part 3: add panel settings`.
- Do not mix generated APKs, unrelated formatting, line-ending churn, or device-local data into a feature commit.

## Required validation

For Android/app changes, run both checks:

```bash
cd android
./gradlew :core-logic:test
cd ../android/compile-check
../gradlew :appmod:compileKotlin
```

If an Android SDK is available, also run the relevant release build. If a check cannot run, report the exact command and blocker; do not claim it passed.

For tools, run the narrowest safe command or `python -m compileall` as appropriate. For documentation, verify every command and link that can be checked locally. Never run provisioning, flashing, ADB, fastboot, HA, or LAN commands from an app-code sandbox.

## Code conventions

- Keep pure parsing, validation, normalization, policy, and state machines in `android/core-logic`.
- Keep Android framework, lifecycle, sensors, rendering, and transport wiring in `android/app`.
- Follow the Android Views approach used by the project; do not introduce Compose or a heavy UI framework without a recorded decision.
- Use friendly labels in the UI and preserve entity IDs only where needed for actions/diagnostics.
- For Home Assistant calls, discover entities and capabilities from live state/registries where possible; do not guess an `input_text`, script, or media entity ID.
- Add tests for new pure logic and regression cases for bug fixes.
- Respect the PowerVR GE8320 budget: no animation loops that run off-screen, no heavy blur stack, and no unnecessary full-screen rebuilds.

## Coordination handoff

At the end of a work block, update the appropriate coordination record:

- `coord/STATUS.md`: current state, files touched, blockers, and next step.
- `coord/REQUESTS.md`: append a clear ask with owner, reason, and done-when criteria.
- `coord/FINDINGS.md`: new verified or uncertain facts, newest first.
- `coord/DECISIONS.md`: approved material trade-offs only.
- `coord/AI_CONTRIBUTOR_NOTES.md`: architecture/review positions, attributed and append-only.

A handoff must include file paths, commands, test results, commit/PR information, and any unverified assumptions. Screenshots or home-state dumps do not belong in this public repository.

## Device and Home Assistant work

Only HANDS performs physical phone, PC, LAN, HA, ADB, fastboot, flashing, or installation operations. App contributors may prepare code/configuration files and a precise request, but must not pretend to have verified behavior on the phone. Mason's approval is required for risky or hard-to-reverse operations.

The master bedroom TV/lights are always manual-only. Never add an automatic fallback, Jarvis action, startup action, ambient action, or test that targets them.

## Pull requests

A PR should state:

- user-visible change and request ID;
- files/modules changed;
- tests and compile checks actually run;
- device/HA verification still needed from HANDS;
- security/privacy review, including confirmation that no secrets or home screenshots were added;
- rollback or disable path when behavior affects kiosk, profiles, automations, or HA service calls.
