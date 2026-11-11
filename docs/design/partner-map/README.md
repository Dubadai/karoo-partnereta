# PartnerETA: partner map

App name: **PartnerETA** (folder `karoo-partnereta`).

Follow a partner's Karoo live link: pink arrow + pink route on your ride map, plus data fields
for distance to them and their ETA at the end of their route.

Round 1 (`mockup.html`, `mockup.png`; rebuild with `python3 build.py`):
- **Setup:** partner name + live link, with a live check of the link. No colour choice: the partner is always pink (a colour Karoo doesn't use).
- **A** map only, **B** + distance field, **C** distance + "Alex ETA" field (clock time, "in 35 min · 14.2 km").
- States: dark mode, no update for 2 min (grey arrow and lines; fields keep the last known distance and ETA with "updated X min ago"), not riding (nothing on the map, fields show `--`).
- The map page is drawn, not a device screenshot (no Karoo was connected). Redo on a real map screenshot.

Data: `GET https://dashboard.hammerhead.io/v1/shares/tracking/<code>` (unofficial, no login, the web page polls it every 5 s):
`state`, `location`, `bearing`, `userTrace` (ridden track), `route.routePolyline` + `waypoints` (while navigating), `activityInfo`, `updatedAt`. `riderName` comes back blank, so the name is set in the extension.

**Decided (2026-11-06):** option C. When updates stop, keep the last known ETA and show "updated X min ago" under it.

**Update timing (planned):**
- Fetch the feed every 30 s by default. Log `updatedAt` on a real ride to learn how often her Karoo uploads, then adjust.
- Between fetches, move the arrow every ~2 s by her recent speed (distance / time between her last two positions):
  along her route line when she's navigating; otherwise straight along her last bearing, for at most 30 s.
- Glide to each new real position. Hold still when `paused` or near-zero speed. After 2 min with no update, stop and go grey.
- ETA counts down the remaining route distance as the arrow moves.

**Idea for round 2: gap when riding the same road.** When you're on the same road heading the same way
(she is near your route or you're on her track, headings within ~45°), the Alex field switches
from straight-line distance to the gap along the road: "200 m ahead / 0:30" (or "behind"). Time gap = when
she passed the point you're at now (we keep her timestamped positions from each fetch), like race
timing; until we have that, distance ÷ your speed. Needs a mockup before building.

Round 2 (`mockup-gap.html`, `mockup-gap.png`): same-road gap.
- On the same road and heading, the left field becomes "Alex ahead" / "Alex behind" with the gap
  along the road. **A** time first ("0:30", "200 m" under it); **B** distance first ("200 m", "0:30" under it).
- Her planned route is drawn under your route, so your route stays visible where they overlap.
- Different roads: the round-1 "Alex" distance field as before.

**Decided (2026-11-06):** option A, time first ("0:30" large, "200 m" under it).

Round 3 (real Karoo map, `device/`): `mockup-map.html` (top fields on the map page, pale vs deep pink
planned route) and `mockup-colours.html` (hot pink, green, cyan, red, violet at 100 m, 1 km and 5 km zoom).
What the Karoo map itself uses: yellow with a black outline for you and your route; orange-red for
highways; blue for water and some main roads; purple-pink edges for bike lanes (close zoom); green
for paths and parks. Her lines get a white outline (two polylines: wide white under, colour on top),
like Karoo's own outlined route, so they still read on the busy zoomed-out map.

**Decided (2026-11-06):** hot pink #E8178A for arrow and ridden track, darker pink #A8105F for her planned route, both lines with a white outline.

**On the device (2026-11-06):** the fields follow the Karoo's own field style (user's call): small icon
left (green flag for ETA, pink arrow for the distance field), title in capitals on the right
("ROBIN ETA", IBM Plex Sans Condensed), value large and regular weight at the Karoo's own size
(ViewConfig.textSize), shrunk only to fit. The line under the value ("in 35 min · 14.2 km",
"updated X min ago") shows only on tall tiles; on a half-width tile a stale value turns grey instead.
Device shots: `device/09`–`12` (first build, Karoo style, stale, not riding).

**One field (2026-11-06, user's call):** only the ETA field remains. Different roads: "ROBIN ETA 20:52"
with the green flag. Same road, same way: "ROBIN AHEAD 0:30" / "ROBIN BEHIND 0:45" with a pink arrow
(up/down) instead of the flag. The straight-line distance field is gone; the map shows where they are.

**What's left is the hero (2026-11-06, user's call, checked on the device with a simulated ride):**
her route *ahead of her* is hot pink with the white outline and white direction chevrons (every
~50 screen px at any zoom, like the Karoo's black chevrons on your yellow route). What she has
ridden is played down: a thinner deep pink line (#A8105F) without outline. The planned route
behind her is no longer drawn. Device shots `device/20`–`25` (simulated ride on a 31 km RideWithGPS
loop: 22 shows the old colours, 23–25 the new ones at 5 km, 2 km and 1 km).

**Map, round 4 (2026-11-06, on the device with the simulated ride):**
- The Karoo's own route: yellow fill ~13 px + black outline ~3 px each side; double "^" chevrons
  across the full fill width, thin strokes; ~240 screen px apart, on straight stretches only
  (Mapbox line-symbol placement). Larger chevrons at the two closest zoom levels: we don't copy
  that, the partner always gets the small ones. `device/35`.
- Partner chevrons now copy that: small double chevrons in the outline colour, 240 px apart,
  straights only, none on your route.
- Your route always wins: extension lines draw above the Karoo's route (no layer order in the
  SDK), so where her route runs on yours her line is left out and two thin rails in her colour
  run along both sides of your route (following *your* route's line, simplified per zoom).
  `device/33`.
- Her lines are simplified to ~1 screen px per zoom, like the Karoo's route.
- Pixel scale measured from the Karoo's scale bar: metres per screen px = 512-tile metres per px
  × 0.66 (zoom 10.87: 1 km = 71 px; checked at 13.41: 200 m = 80 px). `device/34`.
- Styles: round 1 (`device/30`, pinks/cyan/lime/violet) → user liked green and blue → round 2
  (`device/31`–`32`). Next to the yellow route lime rails nearly disappear; cyan stays clear.
- Users will be able to pick the partner colour (setup), with one standard default.

**Decided (2026-11-06):** standard partner colour is **cyan #00E5FF with a black outline** and black chevrons (ridden: #00838F). Lime nearly disappears next to the yellow route. Users can pick another colour on the setup screen (next).

**Round 5 (2026-11-06): partner line improvements, cyan and green (the recommended colours).**
"Before" = as built; "new" = thinner line (7/12 vs 10/16) with a dark tinted outline (navy for
cyan, dark green for green) and small chevrons, so your yellow route stays the boldest; finish
flag in her colour where her route ends. `device/50` (both colours × 3 zooms), `device/52` overview.
- **Ride history off by default** (user's call): only where she's going. Setting "Show ride
  history" will be on the setup screen.
- **Marker: circle with a heading arrow is the standard** (user's call, `device/51`); the arrow
  (same shape as yours) is the alternative, a setting on the setup screen.
- Shared roads: the rails hug your route like a coloured edge, which reads well.

**Decided (2026-11-06):** the new line (thinner, tinted outline, small chevrons) is the standard, in cyan. Green (new) is the recommended alternative colour.

**Finish pin (2026-11-06, user: "perfect"):** a pin like the Karoo's own finish (teardrop with a
checkered disc), in her colour (cyan, navy outline and checks), same size at every zoom, tip on the
end of her route. `device/53`, `56`.

**Shared roads, try 2 (user's idea):** rails in her *outline* colour (navy) instead of cyan, plus
small chevrons in her colour (cyan) over your route, pointing her way, on straight stretches.
On the device the navy rails sit right against your black outline and read as a slightly
thicker dark edge rather than as "her". `device/54`–`55`. Chevrons over your route not yet seen
on the device (that stretch is twisty); unit-tested.

**Join fixed (2026-11-06):** her line used to stop in a rounded stub short of your route (cut at
20 px from its centre, at the nearest sample point). It now ends exactly where its round end touches
your outline: your half width (9.5 px) + half her outlined width, found by bisection. `device/58`
(left). Cyan chevrons over your route on shared stretches confirmed on the device (`device/58`
right, `59` close-up).

**Shared roads, try 3 (preview, `mockup-shared-road.html`):** goal (user): make it obvious you'll
both ride that road. Measured on the device at this zoom: your route = 21 px (13 px #FFFF00 + black);
its width changes with zoom. Options: A your route *cuts* his line (his line runs on to your route's
centre and a short patch of your route is drawn back over the join) + mini markers (small cyan dots
with a navy ring, like his circle marker) along your route; B cut + thin cyan edge + mini markers;
C cut + cyan edge only. The cyan chevrons over your route didn't read well (user).

**Shared roads, try 4 (2026-11-06, on the device):** no outline/edge beside your route (too busy,
user). Your route cuts her line at the join; small markers in her colour sit on your route:
**mini arrows pointing her way (standard)** or dots (a setting). Size test 8 dp vs 10 dp:
`device/61`–`63`. (Line widths: SDK units ≈ 1.8 px; your route measured 20 px at zoom 11.9–15.1.)

**Decided (2026-11-06):** mini arrows 12 dp (standard), dots 10 dp (setting). `device/64`.

**Partner arrows vs the Karoo's chevrons (2026-11-06, on the device):** at zoom 14–15.4 the Karoo
draws small double chevrons ~50–60 px apart on your route; our arrows (every 70 px) sometimes land
right next to one (`device/67`–`68`). Big single chevrons appear from ~16.4 (`device/66`, red because
of a rejoin). The Karoo's map engine places its chevrons per map tile and doesn't tell extensions
where, so "exactly between" can't be guaranteed; see the options in the session notes.

**Decided (2026-11-06):** partner markers on your route every ~220 px (was 70): fewer, so near-collisions with the Karoo's chevrons stay rare. To fine-tune on a live ride. `device/69`.

**Zoom levels on a ride (2026-11-06, on the device, sim ride recording):** reference shots for
tuning marker spacing and line widths per zoom. Scale 200 m (`device/70`, `71`), 100 m (`72`),
50 m, the closest (`73`). Double tap zooms in one level; zooming out needs a pinch, which adb
can't send, so the wider levels (500 m and up) are still missing: pinch out by hand next time.

**Changed back (2026-11-06):** markers every ~70 px again; we found 70 better than 220 on the device.
