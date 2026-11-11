# ETA field: clock size

The clock in the ETA field uses the Karoo's own number size, and with five characters it fills the
whole tile, so it draws more attention than the fields around it. It is also heavier and sits higher
than the Karoo's own numbers.

Round 1 (2026-11-06, `mockup.html` / `mockup.png`, rebuild with `python3 build.py`), on real data pages
(`device/01-data-page-now-light.png`, `device/02-data-page-now-dark.png`):
- **Now**: Karoo number size, fitted to the width.
- **A**: 80 %, regular weight, on the Karoo's baseline (6 px above the tile's bottom).
- **B**: 65 %.
- **C**: 65 % with "in 35 min" under it (the note line, shown on tiles tall enough).

**Round 1 feedback (2026-11-06):** C was good; make it more readable and show the kilometres left on
their route instead of minutes.

Round 2 (`mockup-round2.png`; `mockup.html` now draws round 2):
- **C1**: "14.2 km left", 26 px medium, grey.
- **C2**: same, full text colour.
- **C3**: 28 px, the number bold, full text colour.

**Decided (2026-11-06): C2.** Built: clock at 65 % of the Karoo's number size when the line fits,
"14.2 km left" (or "updated X min ago", grey) in 14 sp medium, full text colour. The line shows on
tiles ≥ 60 dp under the clock (a normal 6-row tile is 76 dp), ≥ 100 dp under the gap value, which
keeps its full size. `device/10` (not riding), `device/11` (sim ride: 19:00, 20.6 km left).

**Fix (2026-11-06, on the device):** the clock was Plex Medium (on the Karoo "sans-serif" is Medium)
and its tops were cut by the title on a 76 dp tile. Now Plex Regular ("sans-serif-light", the weight
of the Karoo's numbers), and its size is also capped by the height left under the title. `device/12`.

**Tweak (2026-11-06):** clock a bit bigger (75 % width cap) and closer to the km line: the line tucks
6 dp into the empty space under the digits. `device/13`.
