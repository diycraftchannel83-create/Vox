# VOX — VoxSculpt

VOX is an Android digital-human voice and video studio built with Kotlin, Jetpack Compose, OpenGL ES 3, and Android media APIs.

## Current project status

**Development / production-readiness work in progress. Not approved for public production release.**

The latest repository state has an Android Release GitHub Actions workflow that successfully compiled and packaged an APK and AAB. A successful build is not evidence that digital-human quality, runtime stability, device compatibility, privacy, licensing, or exported media quality have passed acceptance testing.

The bundled `asian_woman.glb` is currently a small prototype asset (3,996 bytes). It must not be marketed as a photorealistic production avatar.

## Existing capabilities

- Android studio UI built with Jetpack Compose
- Microphone recording and audio-file import
- On-device Android TextToSpeech synthesis
- Audio decoding and approximate audio-band viseme analysis
- GLB/glTF and OBJ avatar import paths
- Facial rig controls, morph targets, and CPU skinning foundations
- OpenGL ES preview renderer
- MP4 export using MediaCodec and MediaMuxer
- Room-backed avatar and export metadata

## Known production blockers

- Replace the prototype avatar with a fully licensed, anatomically complete, high-quality adult digital-human GLB.
- Complete glTF runtime support for skeletal animation, morph-weight animation, and production PBR material maps.
- Replace frequency-band-only lip-sync with phoneme/viseme timestamps for reliable speech articulation.
- Implement and verify multilingual voice selection and language fallback.
- Prove preview/export visual parity and audio/video synchronization.
- Pass target-device performance, memory, thermal, lifecycle, and long-export tests.
- Complete privacy, data-retention, accessibility, licensing, and Play Store release reviews.

See [Production Readiness Gate](docs/PRODUCTION_READINESS_GATE.md), [Realistic Digital Human Roadmap](docs/REALISTIC_DIGITAL_HUMAN_ROADMAP.md), and [Nova Realistic V2 Asset Specification](docs/NOVA_REALISTIC_V2_ASSET_SPEC.md).

## Build

The repository contains a GitHub Actions workflow at `.github/workflows/android-release.yml`. Release signing secrets are required by that workflow.

Do not treat the workflow's successful completion as a production certification. Final build and device validation should be run only after the realistic digital-human asset and renderer changes have reached their acceptance gate.

## Asset licensing

Every bundled or downloadable character must have documented source, creator, license, attribution requirements, and explicit commercial redistribution rights suitable for inclusion in a public Android app. Do not commit assets with unclear provenance or rights.
