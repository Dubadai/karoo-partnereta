# Settings kit in PartnerETA

The shared settings design (karoo-starter `SettingsKit.kt`, its mock pages "PartnerETA" 05–10) now
builds PartnerETA's Setup:
- "Settings" in the bar, the page title under it (PartnerETA, or the person's name).
- People you follow (person icon): each person a `settingsListCard` with their colour as `leading`,
  "+ Add person" is the ADD button.
- A person's page: an Example card with their ETA field (quarter page, their name, distance left as
  set), Name and Live link as `settingsInput`, the link check line, Save as MAIN, then Distance left
  and Ride history as switch rows, Map colour, Marker, Their route and On your route with icons,
  the map preview, and Remove as REMOVE.
- The list page ends with the app rows (update notices, version, github.com/Dubadai/karoo-partnereta).
- The selected option is the shared #3E5560 (light) / #C5D3DA (dark).

`01-list-person-light.png`: the list, a person's top, the end of a person's page (emulator, light).
