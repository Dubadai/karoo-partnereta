# Start alert (round 3, 2026-11-11: built)

While you're riding and your partner isn't yet, PartnerETA tells you once when they start: the Karoo's
own in-ride alert (karoo-ext `InRideAlert`: the Karoo draws it, we give an icon, title, detail,
colours and how long it stays). The rider dismisses it like any Karoo alert.

- When: as soon as their live link says riding, while you're recording and they weren't riding when you
  started. Once per ride per person. No wait for a settled ETA (round 1 had one; our call: we only
  need to know they've left).
- Text (round 3, fitted to the real alert): title "ALEX IS RIDING" (our pick over "ALEX STARTED"),
  then "38.2 km route" / "to Hornsgatan 12": the street of their route's end from one OpenStreetMap
  lookup per ride (sends that point to OSM; after 10 s without an answer the alert goes without it).
  No route: "No route loaded" / "12.4 km away".
- The real Karoo alert: a dark card at the top, title in capitals (about 17 characters, then "…"), up
  to two detail lines of about 17 characters, the icon drawn white on the right, a 15 s countdown bar.
  The background is the person's dark map tone. Photographed on our test Karoo (kept in the private repo).

Shots (`mockup.html`): `01-alert-a`, `02-alert-noroute` (light, dark), `03-settings-light`.
