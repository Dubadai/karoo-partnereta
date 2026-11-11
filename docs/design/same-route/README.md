# Same route: the gap big, the ETA small

Ideas from 2026-11-06:
- When you and your partner ride the **same route** (e.g. the same RideWithGPS route loaded on both
  Karoos), compare the two routes and show how far ahead or behind they are, measured along the route,
  wherever you both are on it (today's gap only works when one of you is on the other's ridden track).
- Then the gap is the big number and the ETA the small line under it.
- A setup option "Distance left": the ETA field with or without "18.2 km left" under the clock.

Round 1 (`mockup.html` / `mockup.png`, rebuild with `python3 build.py`):
- **A** time first: "ROBIN AHEAD 2:30", "ETA 19:00" under it; arrow in the partner colour (up ahead, down behind).
- **B** distance first: "ROBIN AHEAD 1.2 km", "ETA 19:00" under it.
- Different routes: the ETA field with "Distance left" on (as built) or off (just the clock, bigger).
- Setup: "Distance left" toggle with a live field preview, and a line explaining the same-route gap.

How "same route" would be detected: their feed has `route.routePolyline` while they navigate; the
Karoo gives us your loaded route. Same route when most of one lies within ~30 m of the other, same
direction. Gap = (their distance along the route − yours); time gap from when they passed your point.

Screenshots: `device/01-data-page-eta-dark.png` (C2 as built), `device/02-setup-dark.png`.

**Decided (2026-11-06): A, time first.** Built: same-route check (each route ≥ 80 % within 30 m of
the other, same way, sampled every 200 m), gap along the route with race timing, "ETA 19:00" under the
gap, and the "Distance left" option (On/Off like the other setup options; default on).
`device/10` setup, `device/11` field with Distance left off. The gap itself needs both Karoos
navigating the same route to see on the device (unit-tested).

Round 2 (`mockup-round2.png`, top of `mockup.html`): on a shared route the field icon is the
two-person icon (`icon-partners.svg`, "couple user" from SVG Repo, our pick; filled, Karoo green
like the flag). The header is "ROBIN AHEAD/BEHIND" when the name fits, else just "AHEAD/BEHIND" (the
icon stands for the partner). Different routes: the flag, as now. Licence: CC0 (public domain), https://www.svgrepo.com/svg/216185/couple-user; no attribution needed. (Was: check the licence
before the repo goes public.)

Round 3 (`mockup-round3.png`): two tones. Left person = you, right = your partner; the one in front is
Karoo green, the other grey (#7A7A78). Partner ahead: right green; partner behind: left green.

**Built (2026-11-06):** two-tone icon (`res/drawable/ic_partners_ahead|behind|stale.xml`) on any
ahead/behind gap, the flag otherwise. The header is measured on the device: "ROBIN AHEAD" fits a
half-width tile, "ROBIN BEHIND" doesn't, so it shows "BEHIND"; the ETA falls back to "PARTNER ETA".
The stale line is now "2 min ago" ("updated 2 min ago" was cut off). `device/20`–`22` (debug demo data).
