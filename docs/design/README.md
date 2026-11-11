# Design

One folder per feature with a short README of what was decided. The mockups and device
screenshots are kept out of this public repo: they were made on real rides and show real places.

| Folder | What |
| --- | --- |
| `partner-map/` | Partner on your map (pink arrow + route), distance and ETA fields, setup. Option C approved |
| `colour-choice/` | Picking the partner colour on the setup screen. Round 1: A swatches + preview, B list |
| `first-run/` | First open: name with field preview (PARTNER ETA when too long), code after a greyed `live/`, link check, how to add the field |
| `eta-field/` | Clock size in the ETA field: now vs 80 %, 65 %, 65 % with "in 35 min" |
| `same-route/` | Same route: gap big (time or distance), ETA small; "Distance left" option in setup |
| `save-confirmation/` | Save turns green, "Saved ✓" for 3 s, with the link check above it (option A) |
- [map-squash/](map-squash/): the clock keeps its shape when the Karoo shortens a row on a map page (fitEnd).
- [start-alert/](start-alert/): an alert when your partner starts riding (mock round 1).
