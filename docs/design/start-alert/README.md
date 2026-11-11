# Start alert (mock round 2, 2026-11-11)

While you're riding and your partner isn't yet, PartnerETA tells you once when they start: the Karoo's
own in-ride alert (karoo-ext `InRideAlert`: the Karoo draws it, we give an icon, title, detail,
colours and how long it stays). The rider dismisses it like any Karoo alert.

- When: as soon as their live link says riding, while you're recording and they weren't riding when you
  started. Once per ride per person. No wait for a settled ETA (round 1 had one; our call: we only
  need to know they've left).
- Text: "Alex started riding", the route's length, and the finish as a street address (option A,
  picked): one OpenStreetMap lookup per ride, sending that point to OSM; after 10 s without an answer
  the alert goes without it. No route: how far away they are.
- The alert in the mock is drawn after the alert's parts; the real look is the Karoo's. A photo of the
  real one comes before the build.

Shots (`mockup.html`): `01-alert-a`, `02-alert-noroute` (light, dark), `03-settings-light`.
