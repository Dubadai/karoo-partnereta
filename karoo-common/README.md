# karoo-common

The code and resources every DDB Karoo 3 app shares, so they look and behave the same:

- `KarooType`: the Karoo's own field typography (values in Relative, headers, sizes, glyphs, zone
  colours). Measurements: `design-assets/karoo-type/README.md`.
- `SettingsKit` + `ExampleCard` / `SettingsLayout`: the one settings design (design and rules:
  `karoo-starter/docs/design/settings-kit/README.md`), with its colours, `ic_settings_*` icons and
  `bg_*` shapes.
- `UpdateCheck` + `UpdateActivity`: the "Update available" notice.
- `FieldPacer`: paces a field's redraw loop (sleeps until the state or page changes, no polling).
- `streamDataFlow` / `consumerFlow` (KarooFlows.kt): Karoo events and streams as flows.

Package `com.dubadai.karoo.common`. The source of truth is this folder in the workspace; each app
has a copy at `<app>/karoo-common/` with `KIT_VERSION` in the app root.

## Using it in an app

- `settings.gradle.kts`: `include(":karoo-common")`; `app/build.gradle.kts`:
  `implementation(project(":karoo-common"))`.
- `gradle.properties`: `android.nonTransitiveRClass=false`, so app code can keep writing
  `R.drawable.ic_settings_*` and `R.color.settings_*`.
- Kotlin: `import com.dubadai.karoo.common.*`.
- Don't declare `UpdateActivity` in the app manifest: the library brings it.
- An app may override a string (e.g. `update_text`) in its own `strings.xml`; never a colour or icon.

## Changing it

1. Change it here (never inside an app), bump `VERSION` (patch: fix, minor: new piece, major:
   something apps must change for) and add a line to `CHANGELOG.md`.
2. Build karoo-starter, which uses it, and commit to the workspace.
3. Apps take the new version with `bash setup/sync-kit.sh <app>`, **only when they release anyway or
   when the change visibly fixes something in that app** (`gallery.sh <app> --check` tells). So a
   kit change never forces an app update.
