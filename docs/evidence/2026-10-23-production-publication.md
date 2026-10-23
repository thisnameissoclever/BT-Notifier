# Version 1.1.0 production publication

At 2:18 PM America/Los_Angeles on October 23, 2026, Google Play Console confirmed production availability of BT Notifier version 1.1.0, version code 4. The inspected developer was `5493649058553906461`, app `4974722922103654342`, package `com.btnotifier`.

## Observed publication

1. Publishing overview displayed `Last published on October 23, 2026`; the prior pending changes were absent.
2. Test and release displayed `You have no unpublished changes`, latest production release `1.1.0`, and rollout percentage `100%`.
3. Production > Releases displayed `Available on Google Play`, `Released on Oct 23 1:37 PM`, version code `4`, and 178 countries/regions.

These observations establish the full production rollout in Console. They do not establish that every eligible device has received or installed the update. Early release statistics were unavailable or empty; they do not establish crash-free real-world operation.

The published source and submission receipt are on remote `main` at `ac7f098ffab2cf9129a732d65e2bb3c5041cb44e`. The checked application candidate is `cf63fbb42bdc768c63b2d79a73a1606d63c94ca9`. The accepted bundle SHA-256 is `d6be93a7d3faa9bfc24aaaeba9835cc9b6bf760014b65fdf92d4ce39e5e49574`. See the [minimum-SDK verification](2026-10-23-minimum-sdk-correction.md) and [submission receipt](2026-10-23-production-review-submission.md) for checks, approvals, signing and platform limits.

## Original policy warning remains open

During the same observation, this app's [Policy status](https://play.google.com/console/u/0/developers/5493649058553906461/app/4974722922103654342/policy-center) still displayed `App must target Android 16 (API level 36) or higher`, enforced August 30, 2026, under `App updates with these issues will be rejected`.

The accepted code 4 bundle targets API 37. Its publication does not prove clearance of the warning. The reason the warning remains visible is unverified. No release, publishing or protection setting was changed during this check, and no resubmission was attempted.

The existing hourly monitor `bt-notifier-production-review` remains active to observe clearance of this specific warning. Its deadline remains October 30, 2026 at 1:20 PM America/Los_Angeles. It stops for a rejection or necessary user decision, or at the deadline if clearance remains pending. The separately documented February 2027 code-optimization notice remains outside this follow-up.

## Preserved evidence

Screenshots are saved in the primary checkout's ignored `.local-build/features-1.1.0-20261023/min24/` directory:

1. `play-production-available.jpg`: production release availability and expanded version-code summary.
2. `play-policy-still-pending.jpg`: the original target-SDK warning still visible after publication.

This receipt changes dated evidence only. Application, build, dependency and deployment inputs remain unchanged. Native checks and architecture review are not repeated for this receipt; the exact candidate/base reuse assessment and scoped independent review are recorded in the local publication manifest. The previously recorded hardware and platform limits continue to apply.
