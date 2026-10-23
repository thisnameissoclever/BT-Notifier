# Google Play target-SDK policy clearance

At 3:13 PM America/Los_Angeles on October 23, 2026, BT Notifier's Policy status displayed `No issues found`. The same Console page showed a notification naming BT Notifier and stating `A recent policy violation in your app has been addressed`.

The inspected [Policy status page](https://play.google.com/console/u/0/developers/5493649058553906461/app/4974722922103654342/policy-center) belongs to developer `5493649058553906461`, app `4974722922103654342`, package `com.btnotifier`. Its page title was `Policy status | BT Notifier`. The original Android 16/API 36 minimum-target requirement was absent. This observation establishes clearance of that warning after the [production publication](2026-10-23-production-publication.md) of version 1.1.0, code 4, at 100% rollout across 178 countries/regions.

The warning was still visible at the earlier publication check. The cause and exact time of its later clearance are unverified. No additional release, upload, resubmission, setting change or support contact occurred during this follow-up. Automatic protection was retained throughout the approved minimum-API-24 release.

## Source and preserved proof

Application verification remains tied to `cf63fbb42bdc768c63b2d79a73a1606d63c94ca9`, application tree `342da98140fb5b5eb8b4fa53d92b77c4a39696c4`. The uploaded bundle SHA-256 remains `d6be93a7d3faa9bfc24aaaeba9835cc9b6bf760014b65fdf92d4ce39e5e49574`. Source, verification and publication evidence had reached remote `main` at `74a012748ee504466ac2c1280e6685d50a8ba4b6` before this receipt.

The screenshot `play-policy-cleared.jpg` is preserved in the primary checkout's ignored `.local-build/features-1.1.0-20261023/min24/` directory. The page's accessibility text independently confirmed the app identity, `No issues found`, and the addressed-violation notification after a direct page refresh. A DOM snapshot request timed out; accessibility inspection supplied the observed result.

This dated receipt changes no application, build, dependency or deployment inputs. The local policy-clearance manifest records its exact candidate/base, consistency checks, independent scoped review, and the deliberate reuse of existing native verification. Architecture review and repeat native builds/tests are inapplicable to this evidence-only change.

## Scope and monitoring end condition

Production availability and the original policy-warning clearance have both been observed. After this receipt is independently reviewed and merged, the hourly `bt-notifier-production-review` monitor has no remaining condition to watch and should be deleted.

The previously recorded limitations still apply: publication and policy clearance do not establish device adoption, real Bluetooth radio behavior, Android API 24/API 37 execution, or long-duration background reliability. The separately documented February 2027 optimization notice remains outside this follow-up.
