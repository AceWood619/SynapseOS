# Removing stock / permanent install — honest assessment
Mason asked: "can't we get rid of stock OS altogether?" Here's the real picture and a safe path.

## What SynapseOS is right now
- **SynapseOS = a patched LineageOS 21 GSI** running as a **persistent DSU** (a system image stored in /data, booted instead of stock).
- It sits on top of the **stock Android 12 vendor** partition (drivers, firmware, TrustKernel TEE). **That vendor layer must stay** — a GSI has no drivers of its own; wiping vendor = a phone that never boots. So "get rid of stock" can only mean "replace the stock **system** image," never "wipe everything stock."
- In normal use you already **never see stock**. It boots straight into SynapseOS. Stock only exists as a dormant fallback.

## What "permanent install" would actually change
Flash the GSI into the super partition's **system** slot (keeping stock vendor/product), so it boots from super with no DSU.

| | DSU (today) | Permanent |
|---|---|---|
| Boots into Synapse | ✅ | ✅ |
| Stock visible | No (fallback only) | No (removed) |
| /data size | **capped ~7–8 GB** (stock fills super) | full ~27 GB |
| One-command fallback to stock | ✅ `gsi_tool wipe` | ❌ gone |
| Extra boot step | tiny (DSU auto-boots) | none |

**The only material win is storage** (and a bit of tidiness). For a wall panel (dashboard + sensors + voice models) **7 GB is plenty**, so there's no urgent need.

## The risk, stated plainly
- A permanent flash that goes wrong = **hard brick with no recovery on this phone**. mtkclient BROM/preloader access **already failed** here (notes), so the lowest-level rescue is out.
- The earlier permanent attempt booted to **"Can't load Android system."** HANDS later proved TrustKernel ignores the vbmeta digest, so vbmeta wasn't the cause — but the real cause (modified system_a / deleted product / `fastboot -w`) was never pinned down. Repeating it blind risks the same brick.

## Safe path (only if Mason wants it) — do NOT skip a step
1. **R-003 first:** test the DSU escape hatch (`fastboot gsi disable` → boots stock). Proves the fallback works. (reboot; needs Mason)
2. **Prove recovery from backup:** confirm `fastboot flash super C:\c8backup\super.img` works from **fastbootd** (18D1:4EE0, which takes stock fastboot.exe). This is the real safety net — a tested way back from a bad flash. (reboot; needs Mason)
3. Only after 1 and 2 pass: attempt the permanent system flash, with the super backup ready to restore, and root-cause any boot failure instead of retrying blind.

## Recommendation
**Stay on DSU for now.** It already boots straight to SynapseOS, the UX polish makes it feel like its own OS, and v6 makes it self-sufficient. Pursue permanent install only if the 7 GB limit actually bites — and even then, only after steps 1–2 above give us a tested way back. This is a brick-risk decision, so it's yours to call, not mine to make silently.
