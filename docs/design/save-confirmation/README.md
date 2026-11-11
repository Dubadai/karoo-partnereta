# Save confirmation (setup screen)

**Problem:** Save stored the name and link and checked the link, but the result showed next to the
link box, off-screen when you're at the Save button. It looked like nothing happened.

**Options** (`mockup.html`, built by `build.py` from `device/01-setup-save-dark.png`):
- A: the button turns green, "Saved ✓", for 3 s, with the link check right above it.
- B: the button stays yellow; a status line above it.

**Decided (2026-11-06): A.** The link check also still shows by the link box. A bad link shows its
warning above the button, and the button stays yellow.

## Device screenshots
- `device/01-setup-save-dark.png`: before (the bottom of setup, nothing changes on Save).
- `device/02-saved-dark.png`: right after Save.
- `device/03-after-3s-dark.png`: 3 s later, back to Save; the link check stays.
