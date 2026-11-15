# Renderer Migration Decision: Filament

**Status:** Accepted for a staged evaluation; not yet integrated into the production renderer.

## Context

Vox currently renders avatars through a custom OpenGL ES 3 renderer. It already supports the studio backdrop, lighting presets, camera controls, static imported geometry, and CPU-evaluated glTF morph-target position/normal deltas. The current imported sample asset is static and has no skeleton, animation clips, or facial blendshapes, so a renderer change alone cannot make that asset animated or photorealistic.

The next renderer must improve physically based materials and glTF asset support without destabilizing the working OpenGL path.

## Decision

Evaluate Google's Filament as the preferred PBR renderer candidate. Keep `AvatarGlRenderer` as the production fallback until a Filament viewport passes build, device, asset-loading, lifecycle, and performance acceptance checks.

The evaluation must use a single aligned version for `filament-android`, `gltfio-android`, and `filament-utils-android` where used. The glTF loader is a separate published artifact and is the appropriate starting point for GLB/glTF import.

## Compatibility gate

Do not add the newest Filament release blindly to the current app. At the time of this decision, the published 1.80.1 glTF loader metadata declares Kotlin stdlib 2.4.0, while Vox currently pins Kotlin 2.2.10. First test dependency resolution and Gradle/AGP compatibility in an isolated branch or evaluation module. Upgrade Kotlin and related plugins only as a deliberate coordinated change after the baseline CI is green.

## Staged implementation

1. Record baseline compile and unit-test results for the existing renderer.
2. Add Filament dependencies in an isolated evaluation branch/module, not the production source set.
3. Implement a lifecycle-safe Compose viewport using Android's supported surface integration; create and destroy Engine, Renderer, SwapChain, Scene, View, Camera, AssetLoader, and ResourceLoader on the correct lifecycle/thread.
4. Load a licensed GLB with PBR textures and verify camera framing, material correctness, orientation, and resource cleanup.
5. Verify morph-target weights and skeletal animation with a genuinely rigged test asset; do not infer support from a static mesh.
6. Profile frame time, memory, APK/AAB size, and thermal behavior on a low/mid-range Android device.
7. Promote Filament only after all acceptance checks pass; retain a fallback path during rollout.

## Acceptance criteria

- Debug compilation and JVM unit tests pass in CI.
- GLB loads with correct geometry, transforms, UVs, and PBR material maps.
- No GL/native resource leaks after repeated screen navigation and asset replacement.
- Facial morphs and skeletal animation are demonstrated with a test asset that actually contains them.
- No regression to current recording/export flows or supported Android API 24+ devices.
- Asset license and redistribution rights are documented.

## Non-goals

- This decision does not claim Filament has been integrated.
- This decision does not upgrade the app's Kotlin or Android Gradle Plugin versions.
- This decision does not replace the need for a properly modeled, textured, rigged avatar asset.
