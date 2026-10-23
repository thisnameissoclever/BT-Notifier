# Monitoring health, detection history, and device finder

## Scope

Add a monitoring health screen, local detection history with export, and a device finder. Preserve existing watched-device-found alerts. Do not add arrival/departure transition alerts. Per-device schedules, quiet hours, configurable cooldowns, and Wi-Fi rules belong in the backlog.

Resolve the existing scan filtering, cancellation, permission, boot recovery, last-seen, and manual-timer findings before submitting the updated release to Google Play. Keep API 37, minimum API 23, and existing dependencies.

## Monitoring and scanner ownership

Use one application-context Bluetooth scanner with independent consumer leases. Each lease has a unique token and a fresh result map. A lease targets a set of addresses, or explicitly requests unrestricted foreground discovery. An empty targeted set performs no scan. Releasing an old token must never release a replacement or another consumer's lease.

The effective Bluetooth Low Energy filter is the union of targeted leases unless a visible Nearby screen has an unrestricted lease. Nearby releases its lease when its view stops. Finder is always target-filtered and releases its lease on stop. Update Bluetooth Low Energy filters independently of adapter-global classic discovery; keep classic discovery active only while a consumer needs it. Reject late callbacks from obsolete physical scan generations. Guard every permission-protected adapter getter and operation.

The background service acquires a lease only after its Wi-Fi gate permits work. Foreground Nearby or Finder activity cannot bypass that gate for background cycle counts or notifications. Preserve start-to-start interval semantics, serialize cycles, and coalesce Scan Now requests. Stopping or destroying the service cancels scheduling, cycle evaluation, completion, accumulation, and alert work before releasing its lease. Configuration changes that disable monitoring or tighten its gate revoke the current cycle immediately.

## Health screen and startup recovery

Show the actual in-process service state separately from the user's enabled preference. Persist the last completed background scan time and count; a persisted enabled or running value is not proof that a process is alive. Display Bluetooth and notification permissions, Bluetooth state, optional Wi-Fi/location requirements, and Android battery restrictions. Offer the relevant system settings and a Resume action.

Do not add background location permission. When boot, package replacement, or a fresh background restart cannot activate Wi-Fi/location-gated monitoring, retain a needs-resume reason and post a notification that opens a visible activity. Resume there starts the required foreground-service types. Never bypass the Wi-Fi gate or repeatedly report active scanning while work is blocked. If notifications are denied, Health must still report the problem. Ordinary Bluetooth-only startup remains automatic when permissions and Android's service rules permit it.

## Detection history

Add an additive Room migration from version 3 to 4, including a nullable last-observed timestamp on device metadata. Preserve watched devices, aliases, hidden choices, timestamps, settings, and existing migration paths. History stores address, observed name, signal strength, transport, observation time, and source. Coalesce to the latest observation for each device in a one-minute bucket. Retain at most 30 days and 10,000 rows; these are implementation defaults chosen for bounded local storage.

A shared observation repository updates watched last-seen timestamps and retained actual names independently of notification settings and cooldowns. Older observations must not move timestamps backward. Alias fields and hidden choices must survive name updates. Hidden devices are excluded from the visible history and export. The repository deduplicates overlapping consumer observations transactionally and prunes expired/excess records.

Export a stable visible snapshot as UTF-8 CSV through Android's document picker, only after the user chooses a destination. Quote fields correctly and protect untrusted names/addresses against spreadsheet-formula injection while keeping numeric signal values numeric. Export cancellation leaves app data unchanged. Explain that exported copies are controlled by the selected storage destination. Include a confirmed Clear History action that affects history only.

## Finder

Open Finder for a selected nearby or watched address. Show a bounded signal-strength trend, a smoothed reading, and qualitative proximity. Use stronger/weaker language rather than a promised distance in meters. Ignore invalid readings and show a stale/no-recent-signal state when samples stop. Classic discovery may provide intermittent readings. A device that changes its Bluetooth address may require selection again.

Use the current Android Views, navigation, Room, and coroutine dependencies. Add Health and History as bottom navigation destinations, bringing the total to five. Finder is a separate destination with an explicit return action. Keep scans and exports off the UI thread where appropriate, respect view lifecycle, and provide accessible text alongside the graph.

Finder displays signal measurements and trends without computing coordinates, bearing, or distance. Keep the existing `neverForLocation` Bluetooth permission assertion for that scope. Android filters some Bluetooth Low Energy beacons under this assertion; detection is not guaranteed for every advertising device. Reassess the assertion if future code infers geographic location, rooms, or mapped beacon positions. See [Android Bluetooth permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions).

## Acceptance

Use deterministic local tests for lease ownership, generation rejection, filter transitions, timers, timestamps/notification separation, history bounds, CSV escaping, and signal freshness. Use an exclusively owned emulator for additive database upgrade, retained settings/data, the three screens, document export, service controls, blocked/resume states, permission loss, and boot behavior where practical. Record radio and Android-version limits honestly. An emulator without functioning Bluetooth hardware cannot prove real discovery.

Complete fresh independent code, documentation, and functional review before merge. Run relevant checks locally; preserve the filtered Pages deployment for the changed privacy policy. Reconfirm the final base, candidate, dependency identity, artifact hash, and Play version-code availability before submission. The earlier production-submission authorization applies after this feature pass is verified.
