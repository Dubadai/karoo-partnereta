# karoo-common changelog

## 1.0.0 (2026-11-10)

First version as a library. Until now `KarooType.kt`, `SettingsKit.kt`, `ExampleCard.kt` and
`UpdateCheck.kt` were copied into each app; this is karoo-starter's copy of 2026-11-10 (settings kit
update 4 plus fixes: swatch row shares the width, the GitHub line shrinks, `showCard`).

- `KarooType.labelPaint(caps, outline = true)` for the apps whose labels always had the outline
  (Mountaineer, SmallerFields).
- The settings app rows read the app name from the installed app (no `R.string.app_name` needed).
- `UpdateActivity` comes with the library's manifest.
