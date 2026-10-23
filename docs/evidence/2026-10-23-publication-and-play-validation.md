# Publication and Google Play validation

The feature release and its verification record reached remote `main` at `0b4866e06b2b7ae874a730747077bb68cc39677d`. The clean primary checkout was fast-forwarded to the same revision.

## Support-site deployment

The relevant [Pages deployment](https://github.com/thisnameissoclever/BT-Notifier/actions/runs/40009826573) completed successfully. The live [privacy policy](https://thisnameissoclever.github.io/BT-Notifier/privacy-policy.html) returned HTTP 200 and matched the committed HTML byte for byte.

Privacy-policy SHA-256: `df533987df6a1dff8185bc0f25554191e5841dcb35464b6d635dcb4b40b7f878`.

The deployment was the only workflow run observed for that revision. No hosted CI or code review was triggered.

## Google Play outcome

The signed 1.1.0, code 3 bundle was uploaded to BT Notifier's production release draft. Its SHA-256 was `0e075a5a9664fd355f7f121646accb723ad8cecb27c6b1038e1af9aec900eba9`.

Google Play rejected the bundle during validation because Play automatic protection requires a minimum SDK of 24, while this app declares 23. The release enhancement panel confirms that automatic protection is on. The production submission did not occur. Existing production version 1.0.0, code 1 remains unchanged.

The [production draft](https://play.google.com/console/u/0/developers/5493649058553906461/app/4974722922103654342/tracks/4697954557548497029/releases/2/prepare) retains release name 1.1.0 and its English release notes. Play confirmed that the draft changes were saved. The rejected bundle is not an accepted release artifact.

The next release action requires a user decision: raise the minimum supported Android version from Android 6/API 23 to Android 7/API 24 while retaining automatic protection, or retain Android 6 support and disable that protection. No minimum-SDK or protection-setting change was made while that approval was pending. A replacement upload must check version-code availability again.

The Console also displayed a code-optimization notice for existing version 1.0.0 with a February 2027 deadline. Release minification remains unchanged. This separate notice was not the bundle-validation error above.

## Preserved evidence and resources

The signed artifact, local verification manifests, build and signature receipts, runtime evidence, screenshots, and Pages proof are preserved in the primary checkout's ignored `.local-build/features-1.1.0-20261023/` directory. The Play rejection screenshot is `play-minimum-sdk-block.jpg` in that directory.

The exclusively owned read-only emulator and private Android Debug Bridge server were stopped, and the ownership reservation was released. The other Android Debug Bridge server and physical devices were left untouched. No emulator monitor was recreated after verification finished.
