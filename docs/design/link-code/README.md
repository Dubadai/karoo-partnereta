# Live link: type only the code (2026-11-11, decided: A)

Riders can't paste on the Karoo, and today's box says "Paste their Karoo live link". Ask only for the
code at the end of the link (`…/live/AbCd1234`, made up). What to type goes above the box (our settings
rule). The app already accepts a bare code or a whole link, so only the page changes.

- **A:** `live/` fixed and grey inside the box, the code typed after it (as first-run round 1 proposed).
  Needs a fixed start text in the kit's `settingsInput` (karoo-common first).
- **B:** label "Live link code", the example `…/live/AbCd1234` in the line above, a plain box.
  Only the app changes.

Pictures (light only, settings): `shots/01-today`, `02-a-empty`, `03-a-typed`, `04-b-empty`, `05-b-typed`.

**Decided 2026-11-11: A.** Built with karoo-common 1.8.3 (`settingsInput(prefix = "live/", intro = …)`).
