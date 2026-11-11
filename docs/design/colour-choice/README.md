# Colour choice (setup)

Let riders pick the partner's map colour, with cyan + black outline as the standard.

Round 1 (`mockup.html`, `mockup.png`; rebuild with `python3 build.py`), drawn on the real setup screen
(`device/01`–`02`), dark and light:
- **A** swatches (6 dots, chosen one ringed, name + "(standard)") with a live preview: a bit of map,
  your yellow route and the partner's line, outline, chevrons and arrow in the chosen colour.
- **B** a list: each colour as a row with a line sample (fill, outline, double chevrons) and a radio.

Colours: cyan/black (standard), blue/white, green/black, magenta/black, violet/white, lime/black.
The map samples look the same in light and dark mode, as the Karoo's map does.

Waiting for a pick between A and B.

**Built (2026-11-06):** on the setup screen under the link: Map colour (6 swatches; cyan standard,
green recommended), Marker (Circle / Arrow), On your route (Arrows / Dots), Ride history (Off / On),
then a live preview and Save. Map settings apply at once; Save is for name and link. Checked on the
device: picking Green + Dots shows green pin and dots on the map. `device/10`–`13`.

**Added (2026-11-06):** "We recommend the standard settings for the best readability on the map." above the map settings. On your route: arrows standard (they show direction), dots optional. `device/14`.

**Preview made realistic (2026-11-06, user):** a real-looking piece of map (blocks, park, water, streets); your yellow position arrow riding your route with Karoo chevrons; the partner coming down a side road and joining your route ahead, mini arrows where they ride it too; ride history (On) along a cross street, always on a road. `device/17`-`18`.
