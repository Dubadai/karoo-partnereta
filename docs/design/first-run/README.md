# First run: name and live link

What a new user sees when they open PartnerETA the first time. Mockup: `mockup.html` / `mockup.png`
(rebuild with `python3 build.py`, from `mockup.src.html`). The code in the mockups (`AbCd1234`) is
made up: never put a real live link in a mockup, screenshot or commit.

Round 1 (2026-11-06), proposed:
- **Name** with a live preview of the field next to it. The field says "<NAME> ETA" when it fits a
  half-width field, else **PARTNER ETA**, with a note saying why ("Too long for the field…").
  Measured room for the header: 182 px; "ALEX ETA" is 175 px, so about 7 letters fit.
- Gap mode (same road): "<NAME> AHEAD/BEHIND" when it fits, else "PARTNER BEHIND" if that fits
  (it doesn't: 14 letters), else just **AHEAD / BEHIND** (the arrow already says which way).
- **Live link:** `live/` is fixed and greyed out inside the box; the user types only the code after it.
  Help: "Ask your partner for their Karoo live tracking link and type what comes after live/.
  Capital letters matter." A pasted whole link still works.
- **Link check** under the box: "✓ Found · not riding now" or "✕ No live link with this code".
- **After Save:** "All set": the map needs nothing; how to add the ETA field to a ride page, with the
  field as it will look.
- The live link is permanent (a link created Sep 2025 still updates), so this is a one-time setup.

Screenshots: `device/01-setup-map-settings-dark.png` (status bar for the mockup),
`device/02-eta-field-light.png` (a real ETA field on a data page).
