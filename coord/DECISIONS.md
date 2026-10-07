# Decisions log
Newest first. Add an entry only after Mason has approved it.

- **2026-10-07 · Mason likes "Synapse Glass"** (the premium look) — it's the direction. Needs to land on the phone.
- **2026-10-07 · Smart room order (Mason):** rooms reorder by context (panel's room, then active, then night bedrooms), not a fixed list.
- **2026-10-07 · Light resilience (Mason):** keep BOTH local (cync_lan) + cloud (zz_cloud) entities as mutual backups; show ONE dashboard switch that auto-picks the healthy path. Auto-pair only when unambiguous (1 local + 1 cloud per room).
- **2026-10-07 · R-010 recovery gate PASSED** (stock super.img restore proven). Permanent install is now low-risk and available if Mason wants it; DSU remains the default.

- **2026-10-07 · Permanent install (remove stock): PROPOSED, not approved.** Mason asked about it. BRAIN's assessment (`os/PERMANENT_INSTALL.md`): stay on DSU; only material gain is storage (7 GB is enough for a panel); brick risk is real (BROM recovery failed here). Gate R-010 (test escape hatch + prove super.img restore) must pass first, then Mason decides. Vendor stays either way.

- **2026-10-07 · Mason: "merge to main, go, complete the project autonomously."** Everything merged to `main`. BRAIN has a mandate to design, build and test. Risky device actions still get confirmed with Mason in the HANDS chat before they run.

- **2026-10-07 · Two-chat protocol adopted.** HANDS = phone/PC/LAN, BRAIN = research/code. Shared record = `main`.
- **2026-10-07 · vbmeta test: GO (Mason).** Result: boots fine, TrustKernel unaffected, warning not cleared. Phone now runs `vbmeta_reset.img`; factory copy is in `C:\c8backup`.
- **2026-10-07 · DSU userdata = 7 GiB** (the gsid ceiling on this phone).
- **2026-10-07 09:23 · Mason:** (1) **Match the Synapse Glass theme across all of SynapseOS** (system UI, boot animation, launcher, not just the dashboard). (2) **Remove stock when possible** (permanent install is now wanted; each flash step still needs Mason's "go" in chat). (3) **Fix the Power-tap software-only.** No disassembly or button-presser hardware ("defeats the purpose").
