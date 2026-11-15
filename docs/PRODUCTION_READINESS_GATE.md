# VOX Production Readiness Gate

**Status: BLOCKED — not production approved.**  
**Working branch:** `production-readiness/realistic-digital-human`

This gate is intentionally evidence-based. A successful Gradle build, valid GLB structure, or a large morph-target count cannot independently prove photorealism or production quality.

## Confirmed repository facts

- Android Release workflow run #15 completed successfully on 2026-11-15, including `compileDebugKotlin`, signed `assembleRelease`, and `bundleRelease`.
- The workflow is a build signal only; no device lab, visual quality, audio synchronization, crash-free, or Play Console acceptance evidence is included in that result.
- The two bundled copies of `asian_woman.glb` are each 3,996 bytes. The project asset specification already identifies this as a small static prototype mesh, not a production digital-human asset.
- The TTS generator now accepts an explicit BCP-47 language tag and checks whether the installed Android TTS engine supports it. A studio language selector is present for Indonesian, English, Arabic, Japanese, and Korean; actual voice availability and pronunciation quality still require device-level verification.
- The current audio analyzer still estimates visemes from audio frequency bands for microphone/imported audio. A timestamp-driven phoneme-to-viseme builder has been added as an alignment integration point, but a real phoneme alignment source is not yet connected.
- The current custom renderer does not yet consume full normal/roughness/occlusion maps or play all glTF skeletal and morph-weight animation channels.

## Release-blocking work

### P0 — Production character asset

- [ ] Acquire or commission a complete adult female digital-human GLB with documented commercial redistribution rights.
- [ ] Anatomically integrated head, neck, shoulders, eyes/cornea/iris, eyelids, brows, nose, lips, teeth, tongue, and hair.
- [ ] UVs and embedded 2K minimum base-color texture; 4K preferred for close-up use.
- [ ] Embedded tangent-space normal, roughness, and ambient-occlusion maps.
- [ ] Separate physically meaningful materials for skin, eyes, teeth, tongue, hair, and clothing.
- [ ] Facial blendshapes with POSITION and NORMAL deltas; include the complete Vox facial schema.
- [ ] Skeletal hierarchy, inverse bind matrices, normalized vertex weights, and animation clips where required.
- [ ] LODs and mobile texture budgets.
- [ ] Asset manifest records creator, source, license, attribution, and redistribution permissions.
- [ ] Independent visual review verifies neutral face, mouth closure, jaw opening, teeth/tongue continuity, independent blinks, and all facial expressions.

**Important:** Do not replace the current prototype with a procedurally generated primitive and label it realistic. A real production asset must come from a verifiable 3D authoring/generation pipeline and pass the acceptance checklist.

### P0 — Runtime and rendering

- [ ] Complete glTF scene/node transform evaluation.
- [ ] Play skeletal animation clips and morph-target weight animation.
- [ ] Support PBR base color, metallic-roughness, normal, occlusion, and emissive material channels.
- [ ] Support hair alpha modes and eye/cornea transparency correctly.
- [ ] Ensure morph deltas are applied before skinning, as required by glTF.
- [ ] Use a shared scene evaluation path for preview and MP4 export.
- [ ] Verify GPU/CPU performance and memory budgets on low-, mid-, and high-tier Android devices.

### P0 — Speech and facial performance

- [x] Add a deterministic timestamp-driven phoneme-to-viseme timeline builder and unit-test source (not yet executed).
- [ ] Connect a real TTS/speech-alignment provider that supplies phoneme/viseme timestamps and feed those timestamps into the builder.
- [ ] Preserve coarticulation and smooth transitions; avoid frequency-band heuristics as the sole source of phoneme identity.
- [ ] Validate Indonesian and English first, then each additional supported language.
- [ ] Validate P/B/M, F/V, TH, T/D/N, K/G, CH/SH, S/Z, vowels, and silence.
- [ ] Add independently controllable blink, brow, smile, cheek, and head-motion tracks.
- [ ] Keep voice timing and facial timeline deterministic between preview and export.

### P1 — Product reliability and distribution

- [ ] Explicit language/voice selection with device capability checks and clear unavailable-language errors.
- [ ] Handle microphone permission denial, audio focus, interruption, lifecycle, and recorder errors.
- [ ] Bound import file sizes and validate GLB chunks, accessors, indices, textures, and decompression limits.
- [ ] Handle export cancellation, low storage, codec failures, and cleanup of temporary files.
- [ ] Test long recordings and long exports without unbounded memory growth.
- [ ] Complete privacy policy, data-retention/deletion behavior, backup rules, accessibility, and Play Console declarations.
- [ ] Complete crash/ANR monitoring and a device compatibility matrix.

## Changes made on this branch

- Added an explicit BCP-47 language selector for on-device TTS (Indonesian, English, Arabic, Japanese, Korean) and runtime voice-availability checks. Actual voice coverage and pronunciation quality still need device testing.
- Added a timestamp-driven phoneme-to-viseme timeline builder with ARPABET-style phoneme mappings and test cases. It is an integration point, not a claim that phoneme recognition/alignment is already available.
- Added a strict evidence-based digital-human production gate and tests.
- Added repository README and this production checklist.

## Evidence-based validator

`DigitalHumanAssetValidator` now distinguishes structural validation from production approval. Production approval requires all of these explicit evidence flags:

1. Human visual review passed.
2. Commercial redistribution rights cleared.
3. Source and attribution recorded.
4. Device performance passed.
5. Preview/export parity passed.

The validator also reports missing skeletal skin, missing facial targets, missing texture data, and the current incomplete PBR texture pipeline. A model cannot pass production readiness simply because its arrays are structurally valid.

## Build and test policy

Do not trigger final Gradle builds or final test runs until the realistic character asset and the required face/rendering changes are complete. Once that gate is reached, run the complete unit, instrumentation, release-build, and physical-device matrix. Record logs and test evidence here before changing the status to production approved.

## Final approval criteria

Production status may change to **GO** only when every P0/P1 item above is either completed with recorded evidence or explicitly scoped out with a documented product decision, and the final release candidate passes real-device review. No code-only inspection can certify photorealism; the actual GLB must be rendered and reviewed.
