# Decisions log
Newest first. Add an entry only after Mason has approved it.

- **2026-10-07 · Permanent install (remove stock): PROPOSED, not approved.** Mason asked about it. BRAIN's assessment (`os/PERMANENT_INSTALL.md`): stay on DSU; only material gain is storage (7 GB is enough for a panel); brick risk is real (BROM recovery failed here). Gate R-010 (test escape hatch + prove super.img restore) must pass first, then Mason decides. Vendor stays either way.

- **2026-10-07 · Mason: "merge to main, go, complete the project autonomously."** Everything merged to `main`. BRAIN has a mandate to design, build and test. Risky device actions still get confirmed with Mason in the HANDS chat before they run.

- **2026-10-07 · Two-chat protocol adopted.** HANDS = phone/PC/LAN, BRAIN = research/code. Shared record = `main`.
- **2026-10-07 · vbmeta test: GO (Mason).** Result: boots fine, TrustKernel unaffected, warning not cleared. Phone now runs `vbmeta_reset.img`; factory copy is in `C:\c8backup`.
- **2026-10-07 · DSU userdata = 7 GiB** (the gsid ceiling on this phone).
