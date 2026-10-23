# Monitoring, history, finder, and scan reliability implementation plan

**Goal:** Deliver the three approved features and resolve the six reliability findings before the authorized production submission.

**Base:** `3de9b2a90b59cc14ab750e7ecbc2b4ab92892611`.

**Spec:** `docs/features/monitoring-history-finder.md`.

**Architecture:** Share physical scans through owner-specific leases and generations. Keep observation storage and notification policy separate. Add an additive history table, lifecycle-bound feature screens, truthful health state, and an explicit Wi-Fi monitoring resume path under the existing permission model.

**Constraints:** No new dependencies, background-location permission, arrival/departure alerts, or per-device rules. Keep minimum API 23 and target API 37. Run relevant verification locally with one worker and a bounded heap. Do not interrupt shared devices/processes. Preserve filtered Pages deployment when privacy HTML changes. Hold the earlier bundle upload until this pass is complete.

## Architecture review

A fresh read-only architecture review of the base requires lease-specific result maps, generation checks, independent classic/Bluetooth Low Energy lifecycle, gate-based service eligibility, complete cycle cancellation, and monotonic observation ingestion. Sharing is preferred over preemption because classic discovery is adapter-global. Implement these constraints before feature integration.

## Shared contracts

1. `BluetoothScanner.getInstance(context)` returns the application-context singleton. `acquire(owner: String, addresses: Set<String>? = null): ScanLease?` returns a fresh lease or a reported blocked result. Null addresses mean foreground unrestricted discovery; empty targeted sets are idle. `release(lease)` is idempotent. `ScanLease.devices` is a `StateFlow<Map<String, ScannedDevice>>` containing that lease's fresh eligible results only. `scanner.devices` remains a global display cache; it is never a background cycle result. `scanner.status` reports `ScannerStatus` with `isScanning`, `bleScanning`, `classicScanning`, and nullable `error`. `scanner.isBluetoothEnabled` and `scanner.hasPermissions` are safe getters.
2. `ObservationRepository.get(context).record(devices: Collection<ScannedDevice>, source: DetectionSource)` persists observations. `DetectionSource` has `BACKGROUND`, `NEARBY`, and `FINDER`. The repository owns monotonic watched last-seen/name updates, history coalescing, hidden-device filtering, and retention. Alert switches and cooldown never suppress ingestion.
3. `MonitoringStatus.state` exposes `StateFlow<MonitoringSnapshot>` with `running: Boolean`, `phase: String`, `nextScanAt: Long?`, and `error: String?`. Persisted statistics live in `PreferencesManager`: `lastBackgroundScanAt: Flow<Long>`, `lastBackgroundDeviceCount: Flow<Int>`, `monitoringResumeRequired: Flow<Boolean>`, and `monitoringMessage: Flow<String>`. Provide `recordBackgroundScan(at, count)` and `setMonitoringResumeRequired(required, message)` suspend functions. Add distinct-until-changed to configuration flows so writing statistics does not restart cycles.
4. `MonitoringServiceStarter.start(context, userInitiated: Boolean): Boolean` guards service entry and requests a visible resume when background Wi-Fi activation is ineligible. MainActivity and Settings call it from visible user activity; BootReceiver calls it with false. Health's Resume action reuses MainActivity's guarded permission/start path.
5. Finder receives string navigation arguments `address` and `displayName`. Entry buttons on Nearby and Watch List navigate there. Finder releases its scanner lease when its view stops and may record target observations through the shared repository.

## Implementation ownership

1. Persistence/history worker: detection models/DAO/repository, Room migration, monotonic DAO updates, CSV and History UI, scoped tests.
2. Scanner/reliability worker: scanner leases/driver/policies, service lifecycle and boot starter, monitoring status, PreferencesManager fields, NearbyViewModel timers/leases, NotificationHelper resume action, scoped tests.
3. Health/finder worker: Health and Finder screens/layouts, signal graph and pure signal model/tests, using the shared contracts.
4. Main implementer: navigation and entry buttons, MainActivity/Settings integration, versioning, public/privacy/release documentation, backlog, serialized local builds, functional evidence, final review, merge, and production submission.

Workers share the checkout and must preserve one another's edits. Only the main implementer runs Gradle or device commands, so heavy verification never overlaps. Workers write meaningful tests before their corresponding pure policies, and request a serialized red/green run when ready. Android integration receives functional proof after a coherent build; do not call unrelated compile failures a red regression result.

## Verification and release

1. Reproduce relevant timer, timestamp, lease, CSV, history-bound, and stale-signal failures with focused tests before the fixes.
2. Run the coherent unit suite, lint, debug/release assembly, and local workflow/manifest checks with one worker and a bounded heap.
3. Verify a populated version-3 database and existing settings update to version 4. Test Health, History, Finder lifecycle, export/cancellation, service stop/start, permission loss, and boot/resume paths on an exclusively reserved emulator. Document hardware and platform gaps.
4. Obtain fresh final correctness and functional reviews against immutable candidate/base revisions; address valid findings and repeat only affected checks. Fetch the final base and integrate movement before delivery.
5. Update privacy/listing documentation and verify the relevant Pages deployment after merge. Confirm the final bundle signature, package, version code, and hash.
6. Upload the verified combined release and submit it for production rollout under the user's existing authorization. Record actual Play state; upload, submission, review, and availability are separate outcomes.
