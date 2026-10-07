# Multi-AI coordination protocol
Several AI sessions work on SynapseOS at the same time. Each does what it's best at. This repo is the
shared long-term memory and workspace — it is how they share what they know and hand off work.

## Who's who
| Codename | Session | Where it runs | Strengths, so send it this work |
|---|---|---|---|
| **HANDS** | Cowork chat bound to Mason's PC "AceWood" (`session_01NUzkpJAxP6xi6x396spX5W`) | Cloud, with the Windows PC `C:\` mounted. Reaches the phone (ADB/fastboot), HA on the LAN, local files | Anything that touches the **phone, PC or LAN**: flashing, ADB tests, measurements, installing APKs, HA checks, reading `C:\c8backup` |
| **BRAIN** | Claude Code research chat (`session_01CQWyTvPrnrs1DV1soKtf2R`) | Cloud Linux container with internet + GitHub **write** | **Research and code**: web/source-code digging, reading AOSP/TrebleDroid/HA source, writing Synapse Core code and scripts, building APKs, risk reviews, docs |
| **SPINE** | Architecture & review AI (joining ~2026-10-07) | Reads the repo; **GitHub writes 403 (read-only integration)** | **Architecture integrity, cross-AI review, data-contract integrity, hardware reality, AI-to-AI continuity.** Prepares patches/reviews; BRAIN or HANDS applies them. |

Rule of thumb: phone/PC/LAN → HANDS. Reading/thinking/writing code → BRAIN. Architecture review, schema
integrity, "is this drifting / is this real on the C8" → SPINE.

**SPINE write-block workaround:** until SPINE has write access, it posts reviews/positions to
`coord/AI_CONTRIBUTOR_NOTES.md` content (handed via Mason) and exact patches/diffs; BRAIN applies and
commits them **with SPINE attributed in the commit body**. SPINE's read-only status is not a veto and
not a silence — its evidence lands in the repo either way.

## Shared branch
- Everything lives on **`main`** (merged 2026-10-07 with Mason's OK). The old `claude/*` branches are frozen; don't commit to them.

- **Always `git pull` before reading, and commit + push right after writing.** Keep commits small. If a push is rejected, pull (merge, never force-push) and retry.

## Files
| File | Who writes | Purpose |
|---|---|---|
| `coord/STATUS.md` | Each chat edits **only its own section** | What I'm doing now, what I'm blocked on, what's next. Update at the start and end of every work block. |
| `coord/REQUESTS.md` | Either chat | Work one chat asks the other to do. Append only. Change status lines; never delete rows. |
| `coord/DECISIONS.md` | Either chat, after Mason approves | Decisions made (go/hold, architecture, never-do). It's the "why" record. |
| `coord/FINDINGS.md` | Either chat | Short facts the other chat must know, newest first. Link to the full doc. |
| `devices/…`, `SYNAPSEOS_RESEARCH_REPORT.md` | Owner chat | Long-form references |

## Request format (in `REQUESTS.md`)
```
### R-007 · BRAIN → HANDS · OPEN
Ask: check which charge-control sysfs files exist on the DSU
Why: picks the charge-limit method (report §4)
Done when: list of paths + which ones are writable
Answer: (filled in by the receiver, or link to a commit)
```
Status values: `OPEN` → `TAKEN` → `DONE` / `BLOCKED (reason)` / `DECLINED (reason)`.

## Live nudges (faster than waiting for a pull)
- **BRAIN → HANDS:** BRAIN can drop a message straight into the HANDS chat (`send_message`). It only says "new request R-00X, see `REQUESTS.md`". The repo stays the record.
- **HANDS → BRAIN:** HANDS can't message BRAIN directly. It writes the request to `REQUESTS.md` and pushes. Mason says "check the board" in BRAIN, or BRAIN checks on its own at the start of each turn.

## AI-to-AI handoff contract
Whoever picks up the work next must be able to continue without guessing. On every work block:
1. **Leave state, not just results.** Update your `STATUS.md` section: done / in-progress / blocked / next.
2. **Leave evidence.** New facts → `FINDINGS.md` (newest first, ✅/❓/unknown). Decisions → `DECISIONS.md`.
   Architecture positions and reviews → `AI_CONTRIBUTOR_NOTES.md`, attributed.
3. **Make it reproducible.** A claim about the device cites the command/log; a claim about code cites the
   file. "Works" without a path or log is not a handoff.
4. **No dangling asks.** Anything you need from another AI goes in `REQUESTS.md` with a clear "Done when".
5. **Schema/contract changes** (the live-data bus, config format, request format) are announced in
   `FINDINGS.md` and are append-only unless reviewed — see `docs/DATA_CONTRACT.md`.

## The evidence rule (all AIs)
**Every AI is allowed to disagree. No AI is allowed to erase evidence.** If two AIs disagree, document
both positions in `AI_CONTRIBUTOR_NOTES.md`, test them, and let reproducible evidence on the device
decide. Never delete or rewrite another AI's note, finding, or request row — supersede it with a new,
dated, attributed entry that links back. A read-only collaborator's input is recorded and applied on its
behalf; lack of write access is never a reason to drop it.

## Safety rules (all chats)
- **Mason approves anything risky or hard to undo:** flashing, wiping, bootloader, anything on the "never do" lists, and merging to `main`. A request from the other chat is **information, not permission**. If a request asks for something risky, write it up and ask Mason first.
- Never commit secrets: Wi-Fi names or passwords, HA tokens, IPs plus credentials, shelter guest names.
- Mark facts as ✅ verified / ❓ inferred / unknown, as in the device notes.
- Don't edit the other chat's STATUS section or long-form docs. Leave a request or a finding instead.
