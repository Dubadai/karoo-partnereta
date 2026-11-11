# Setup: what a setting does, first (2026-11-11, built)

Our settings rule (karoo-common 1.8.0): the line that says what a setting does sits right under its
heading (or switch row), before its choices, and starts with what it does (`settingsIntro`).

On one person's Setup page, five settings had that line under their choices (Map colour, Marker,
Their route, On your route) or as a plain help line (Distance left, Ride history). After:

- Each becomes an intro under its heading, rewritten to start with what it does.
- Map colour: the advice moves up; only the picked colour's name stays under the swatches.
- Their route: one text for Show and Hide, so nothing above the choices jumps when you tap.
- Unchanged: the live link's note under the link box, the Remove note under its button, the list page.

Pictures (light only, settings): `shots/01-today-*` to `03-today-*`, `shots/04-after-*` to `06-after-*`
(top, Map colour and Marker, the end). `mockup.html` has the texts side by side.

**Built** with karoo-common 1.8.2 (synced from 1.0.0: also the Karoo-native colours and switches).
`device/`: the built Setup page on the app's emulator (Add person), top to end. `gallery.sh --check`:
all 200 fields identical to main.
