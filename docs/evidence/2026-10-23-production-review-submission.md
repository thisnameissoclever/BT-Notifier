# Version 1.1.0 production submission

On October 23, 2026, Google Play accepted version 1.1.0, code 4, declaring minimum API 24 and target API 37. This replacement follows the user's approval to require Android 7 while keeping automatic protection enabled.

Source and verification reached remote `main` at `0fd86eaca4495b14dc0198d6356a2925dfa4f076`. The checked application candidate was `cf63fbb42bdc768c63b2d79a73a1606d63c94ca9`. The [minimum-SDK evidence](2026-10-23-minimum-sdk-correction.md) records fresh local checks, retained-data verification, signature verification, and platform limits.

Uploaded bundle SHA-256: `d6be93a7d3faa9bfc24aaaeba9835cc9b6bf760014b65fdf92d4ce39e5e49574`.

## Observed Console outcome

The accepted bundle table displayed `4 (1.1.0)`, API levels `24+`, target SDK `37`, and `Enhanced`. Automatic protection was retained; no protection setting was changed.

The production preview specified 100.0% rollout in all existing targeted countries. Its two warnings concerned the approved compatibility reduction and the absence of a deobfuscation file. Release minification is disabled. Play reported 1,133 unsupported device-catalog entries; that count is not a count of affected users.

The English default listing was updated from `docs/play-store-listing.md`, including the new features, history retention/export details, and Android 7 requirement. The release notes also disclose the Android requirement.

The final confirmation sent exactly two changes for review:

1. Production version 1.1.0: Start full rollout.
2. English default store listing: Change full description.

After confirmation, [Publishing overview](https://play.google.com/console/u/0/developers/5493649058553906461/app/4974722922103654342/publishing) displayed `Changes in review`. Google's quick checks were still running and its text said the changes would enter review after those checks succeeded. This establishes an acknowledged submission request, not approval or production availability.

Managed publishing was off, so Google is configured to publish automatically after approval. At the last observation around 1:15 PM America/Los_Angeles, the last published date still referred to the prior release. Availability of code 4 remains a separate verification step.

The submission screenshot is preserved in the primary checkout's ignored `.local-build/features-1.1.0-20261023/min24/play-production-submitted.jpg`. The same directory retains the exact uploaded artifact and local verification manifests. The task-owned emulator reservation and private device server were released before submission.
