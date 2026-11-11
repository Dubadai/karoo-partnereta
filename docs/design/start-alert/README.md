# Start alert (mock round 1, 2026-11-11)

While you're riding and your partner isn't yet, PartnerETA tells you once when they start: the Karoo's
own in-ride alert (karoo-ext `InRideAlert`: the Karoo draws it, we give an icon, title, detail,
colours and how long it stays). The rider dismisses it like any Karoo alert.

- When: you're recording, they weren't riding when you started, they've ridden 5 minutes and their ETA
  has stayed within 2 minutes for 2 minutes (at the latest at 10 minutes). Once per ride per person.
- Open pick: the finish line. A: street address from OpenStreetMap (one lookup per ride, sends that
  point to OSM). B: no lookup, where their finish is from yours.
- The alert in the mock is drawn after the alert's parts; the real look is the Karoo's. A photo of the
  real one comes before the build.

Shots (`mockup.html`): `01-alert-a`, `02-alert-b`, `03-alert-noroute` (light, dark), `04-settings-light`.
