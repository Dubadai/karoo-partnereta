# Short tiles: keep the small line (2026-11-11, built: option B)

The smallest-text rule says: in a short tile the big number gives way, never the small text. Today
the ETA field leaves out "14.2 km left" in tiles under 150 px (a one-row tile, 141 px), so the clock
can stay big. Words that matter ("paused", "no connection", "3 min ago") already stay down to
112 px, with a smaller clock (`00-today-paused-small-*`).

**After:** "14.2 km left" stays in 141 px tiles too, and the clock shrinks above it. Bigger tiles
(quarter, half, full) are unchanged.

**Map page:** with a route loaded, the Karoo lays a 141 px row out at 123 px without telling the
app, and shrinks the picture under the header to fit (fitEnd, see `map-squash/`). Two ways:

- **A:** the clock and the small line stay one picture and shrink together. The small line goes
  to about 15 px tall, under the 16.5 px minimum.
- **B (picked, built):** the small line is a row of its own at true size (19 px); only the clock shrinks.

Pictures (fields drawn by the app's own code in the field gallery; the map tiles are those 141 px
fields laid out at 123 px the way the Karoo does it):

- `00-today-paused-small-*`: today, a word that matters already keeps its line.
- `01-before-small-*`, `02-before-row-*`: today, 141 px tiles without "km left".
- `03-after-small-*`, `04-after-row-*`: after.
- `05-map-before-*`: map page today. `06-map-a-*`: option A. `07-map-b-*`: option B.
- `08-built-map-*`: the built code, laid out at 123 px by the gallery (no simulation): the line
  stays 19 px, the clock gives way (33 px digits in the half-width row).

**Built:** the clock and the small line are two images (`field_partner.xml`: `body` and `note`); the
"extra line" flag is gone, so every small line shows from 60 dp up. `gallery.sh --check`: only the
"km left" fields in one-row tiles changed (40 of 200); every other field is byte-identical.
