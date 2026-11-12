# karoo-common changelog

## 1.8.6 (2026-11-12)

- `settingsChoiceCards`: the picked radio is Apple's: a solid blue disc (the ring's outer size, 22 dp)
  with a white dot (7.5 dp). Options not picked keep the thin grey ring. Mock round 2 and emulator
  shots: `docs/design/apple-radio/`. Mock kit: `choiceCards(opts, sel, { radio: 'apple-picked' })`.

## 1.8.5 (2026-11-12)

- `settingsChoiceCards`: the blue ring's corners are concentric with the card's. The ring sits 4 dp
  outside a 20 dp card, so its outer corners are 24 dp (stroke middle 22.5 dp); it had 20 dp and
  looked tighter than the card. Mock kit `choiceCards` matches. Shots: `docs/design/choice-cards/ring-corners-*`.

## 1.8.4 (2026-11-12)

- Icons centre on their title's capital letters, not on the title's text box. The box has font room
  above the letters, so a box-centred icon sat about 2 dp too high (measured on a Karoo: 3.5 px).
  Applies to `settingsHeading` and `settingsSwitchRow`, `settingsNavRow`, `settingsInfoRow` and the
  version row. Measured from the laid-out first line before drawing (any font or size); only the
  icon's translationY changes, so no heights or layouts move.

## 1.8.3 (2026-11-11)

- `settingsInput(…, prefix = "live/")`: fixed start text, grey and not editable, inside the box; the
  rider types only what follows (PartnerETA's live-link code: the Karoo can't paste). Without a
  prefix nothing changes. Mock kit: `input(label, value, { prefix })`.
- `settingsInput(…, intro = "…")`: what to type, between the label and the box, like `settingsIntro`
  (help first applies to inputs too). Without it nothing changes.

## 1.8.2 (2026-11-11)

- A divider right under a switch row keeps 12 dp above itself, so the last switch of a list doesn't
  sit on the line (help used to give that room before it moved above the choices). Also the line
  before the last rows (`settingsAppRows`). Apps can drop their own spacers there (UpAhead's report).

## 1.8.1 (2026-11-11)

Our rule: an icon always sits next to its title (the first line), never in the middle of a title +
lines block.

- Version row (last row of every page): the info icon next to the app's name.
- `settingsNavRow` with a status line: the icon next to the title; the arrow stays in the middle.
- `settingsInfoRow`: the icon next to the note's first line when it wraps.
- `settingsListCard`: the leading swatch or picture next to the name when there are status lines.

## 1.8.0 (2026-11-11)

Our rule from today: what a setting does goes right under its heading, before its choices, never
under them (mock: `docs/design/help-first`).

- `settingsIntro(text)`: that line, no gap above, 12 dp below. `settingsHelp` stays for other notes.
- `settingsSegmented` has 14 dp under it (its explanation now sits above it), like the choice cards' 12.
- The kit's own last rows use it (update notices). Apps move each `settingsHelp` that explains a
  setting to a `settingsIntro` right after its heading when they take this version.

## 1.7.0 (2026-11-11)

- `settingsChoiceCards(options: List<Triple<title, line?, value>>, selected, onPick)` (from UpAhead, our
  pick): one choice from a longer list as cards, a title, an optional short line, a radio at the right;
  the picked card with a 3 dp Apple-blue ring (#007AFF, night #0A84FF, colour `settings_select_blue`)
  just outside it and a filled radio. With the green switch, the one exception to Karoo-native colours.
- 12 dp under the last card, so it doesn't sit on the divider below.
- `settingsChoiceList` now draws these cards (titles only), so apps calling it change look without code.
- `SettingsLayout` lets the ring draw into the form's side margin (clipChildren / clipToPadding off).
  A form inside its own ViewGroup needs both off on that group too.
- Seen on the emulator: `docs/design/choice-cards/`. Mock piece: `choiceCards` in the mock kit.

## 1.6.2 (2026-11-11)

No visual change. A comment in `SettingsKit.kt` named a person (the kit goes into public repos);
`setup/sync-kit.sh` no longer copies `docs/` (design mocks) into apps.

## 1.6.1 (2026-11-11)

- Switches: still the Karoo's own (Android) switch, only recoloured: the knob is white in every state
  (with its own shadow), only the track changes, full Karoo green (#0DBA75) on, grey off (night
  #636363). In 1.6.0 the knob turned green and the track stayed 30 % see-through. Colours
  `settings_switch_off`, `settings_switch_thumb`. Seen on the emulator: `docs/design/native-colours/device/`.

## 1.6.0 (2026-11-11)

Settings and Setup pages in the Karoo's own colours (design: `docs/design/native-colours`), read from
the Karoo 3's Settings app. Visible on every settings page: take it with the next release.

- Segmented control like the Karoo's pill control: picked option light blue-grey (`k2LightBlue`
  #9FB3BD) with dark text (`k2Black` #1B2D2D), was slate with white text.
- Switches: the Karoo's own (Android) switch, Karoo green when on (`success_green_600` #0DBA75), grey
  when off. New colour `settings_switch_on`.
- Picked rings (`SettingsSwatch`, `settingsChoiceList`): the Karoo's focus ring, `k2DarkBlue`
  #214559 (night #9FB3BD). New colour `settings_ring`. Apps that used `segment_selected` for text or
  rings should use `settings_title` / `settings_ring`: it's now too pale for that.
- Neutral Karoo greys instead of warm ones: help text, choice ring, input border, Example card, tile
  edge, night dividers and track. Yellow `yellow_500`, red `red_600`, OK green `success_green_700`
  (night `success_green_600`), Add button `k2LightBlue` (night `k2DarkBlue`), dark button text.

## 1.5.0 (2026-11-11)

Nothing changes for apps that don't use it (no visual change, no release needed).

- `FieldPacer`: a field's redraw loop sleeps until its state or the page on screen changes (or the
  next redraw is due) instead of waking every 40–100 ms to look. Same redraw timing; other inputs
  (settings, night mode) are seen within a second. Unit tests in `src/test`.
- `streamDataFlow`, `consumerFlow`, `StreamState.field` / `single` (KarooFlows.kt): the flow helpers
  every app had its own copy of. An app that keeps its copy keeps using it.

## 1.4.0 (2026-11-10)

Added for UpAhead; nothing changes for apps that don't use it.

- `settingsChoiceList`: one choice from a list too long for a segmented control (a row per option, a
  ring at the right, the picked one on a grey card).

## 1.3.0 (2026-11-10)

- `KarooField.views(…, footerPx = n)`: a bottom band kept at true size too (top fixed, middle in the
  room left, bottom fixed), for a value line under the number (Mountaineer's "+ 0:18 VS PR", grade
  boxes). Without `footerPx` nothing changes.

## 1.2.0 (2026-11-10)

- `KarooField.views(context, bitmap, headerPx)` + `KarooField.headerBandPx(tileH)`: a field drawn as
  one picture keeps its header at true size when the Karoo shortens the row (map page with a route:
  141 → 123 px squashed the whole picture to 0.87). Use it instead of one `fitXY` ImageView.

## 1.1.0 (2026-11-10)

Added for UpAhead; nothing changes for apps that don't use them (no visual change, no release needed).

- `Physics` and `Wind` (Physics.kt): Mountaineer's cycling physics (power ↔ speed on a grade, headwind),
  so apps share one copy. Mountaineer still has its own until it syncs.
- `settingsRankList`: a list the rider orders by dragging a handle (icon priority in UpAhead), with
  the kit icons `ic_settings_handle` and `ic_settings_priority`.

## 1.0.0 (2026-11-10)

First version as a library. Until now `KarooType.kt`, `SettingsKit.kt`, `ExampleCard.kt` and
`UpdateCheck.kt` were copied into each app; this is karoo-starter's copy of 2026-11-10 (settings kit
update 4 plus fixes: swatch row shares the width, the GitHub line shrinks, `showCard`).

- `KarooType.labelPaint(caps, outline = true)` for the apps whose labels always had the outline
  (Mountaineer, SmallerFields).
- The settings app rows read the app name from the installed app (no `R.string.app_name` needed).
- `UpdateActivity` comes with the library's manifest.
