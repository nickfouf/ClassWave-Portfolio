# Verification of the public snapshot

Checks performed on 8 November 2026.

| Check | Result |
| --- | --- |
| Backend test suite | 6 passed |
| Android local unit tests | 9 passed |
| Android debug assembly | Successful |
| Gradle wrapper | Included and used for the Android build |
| Source comparison | Application source matched the latest local thesis project before the documented publication adjustments |
| Public file selection | Reviewed allowlist of application source, example configuration, selected screenshots, documentation |

## Environment

Backend tests used Node.js 24.22.1. Android validation used Gradle 8.13, JDK 21 and Android SDK 36 on Windows in an ASCII-only directory. Dependency resolution required network access.

The public snapshot adds the Gradle wrapper JAR, documents the Node.js 22 minimum required by mediasoup, includes example configuration and omits logging of registration request bodies.

Screenshots come from the application's demonstration material. The silent demo excerpt contains application footage from the final presentation video.

## Scope

Automated checks cover selected behaviors and build reproducibility. The application has also been demonstrated in recorded device sessions. Interactive device and multi-participant calls were not rerun during this publication pass. The README records the deployment and evaluation work that remains.
