# Claude ↔ ChatGPT bridge

_Set up by Claude (BRAIN), 2026-10-16 18:30 PDT, at Mason's request ("work with ChatGPT… set something up between you")._

**Roles (Mason, 2026-10-16 18:17):** Claude owns SynapseOS: architecture, the redesign, merges to `main`, and phone installs. ChatGPT Desktop Commander is the collaborating engineer: review, debugging, investigation, verification.

## Channel
Neither AI can message the other directly. The shared space is Mason's PC:

| Path on Mason's PC | Direction |
|---|---|
| `C:\SynapseOS-bridge\to_chatgpt.md` | Claude → ChatGPT (append-only) |
| `C:\SynapseOS-bridge\to_claude.md` | ChatGPT → Claude (append-only) |
| `C:\SynapseOS-bridge\outbox-chatgpt\`, `shots\` | patches, screenshots, logs |

Claude can only reach the PC while Mason has the Claude desktop chat open, so ChatGPT signals "Claude: check the bridge" through Mason. Decisions get mirrored here on `main`.

## Rules
1. GitHub `main` is the only source of truth. The phone only gets CI builds from `main` whose SHA-256 matches `builds/synapse-core-latest.json`.
2. Only Claude installs to or changes the phone. ChatGPT stays read-only on the device (dumpsys, logcat, screencap; no installs, taps, settings changes, or reboots).
3. ChatGPT works on local `chatgpt/<topic>` branches and hands off with `git format-patch`; Claude reviews and lands the changes.
4. Never flash, wipe, or touch the bootloader or vbmeta, and never target the master-bedroom TV or lights.
5. No secrets in bridge files or git.

## Known issue
The PC's GitHub credential is stale: `git push` from `C:\SynapseOS` opens a sign-in pop-up whose link is broken, and the push hangs. Because of that, ChatGPT's 8 local commits (`02f95e7..0994bbf`) never reached GitHub. They're exported as `C:\SynapseOS-bridge\outbox-chatgpt\chatgpt-local-8-commits.patch` and pending review and landing by Claude.
