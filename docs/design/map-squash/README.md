# Map page squash (2026-11-10)

On a map page with a route, the Karoo shortens a row from 141 to 123 px without telling the app.
The ETA field's header is real text, so it kept its size, but the clock picture under it was shown
with `fitXY` and got squashed in height (digits too wide and flat).

**Fix:** the clock picture now uses `fitEnd`: in a shortened row it shrinks in proportion and sits
bottom right, like the Karoo's own numbers. Tiles at their full size are unchanged (gallery
pictures byte for byte the same).

Pictures: the gallery's 141 px tiles laid out at 123 px (white below is the gallery's tile).

- `01-before-small-*`: before, squashed.
- `02-after-small-*`: after.
- `03-after-away-small-light.png`: the two-line state ("3.4 / km away").
- `04-after-row-*`: full-width row.
