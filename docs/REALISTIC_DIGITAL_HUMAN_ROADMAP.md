# Vox Realistic Digital Human — Product & Engineering Plan

Status: approved direction (Option C: ready-made realistic avatars + AI Face Generator).
Branch: main.
Baseline: Android/Kotlin/Jetpack Compose, OpenGL ES 3 custom renderer, GLB importer.

## Product goal

Move Vox from simple procedural/puppet-like avatars to convincing human digital characters suitable for talking-head video. The goal is high visual realism, believable facial motion, and consistent preview/export output. "Perfect" realism is not a measurable promise; acceptance is based on the criteria below and real-device review.

## User-facing modes

### 1. Ready-made Human Library
- Ship a curated roster of high-quality adult human characters, with varied masculine/feminine appearances, skin tones, hairstyles, and clothing.
- Each character is a complete, licensed digital-human asset, not a generated head mesh assembled from primitive shapes.
- Each asset includes embedded textures, eye/teeth/tongue geometry, facial rig metadata, thumbnail, attribution, license, and performance tier.
- The first release should include a small number of genuinely high-quality characters rather than many low-quality placeholders.

### 2. AI Face Generator
- Text-to-character: create a new fictional adult character from a prompt.
- Reference-assisted character: allow an optional user-supplied image as visual guidance, subject to consent and rights confirmation.
- Generated output must be a 3D-ready asset package (GLB + textures + facial morph targets/rig metadata), not merely a 2D portrait.
- Do not represent a 2D image generator as a working 3D avatar generator.
- The generation service is a replaceable backend provider. Keep provider credentials on the server; never embed secret API keys in the Android app.
- Before choosing a provider, verify commercial usage rights, image/face policies, export formats, per-generation pricing, data retention, and availability in the target markets.
- Provide progress, retry, cancellation, and clear error states. Generated assets are downloaded only after validation.

## Digital-human asset contract

Each production character should provide:
- glTF 2.0 binary GLB with all referenced geometry, buffers, and textures embedded.
- Anatomically integrated head, neck, shoulders, eyes, eyelids, brows, teeth, tongue, and hair.
- UVs and high-resolution base color; normal, roughness, and ambient-occlusion maps where supported by the renderer.
- Tangents where required by normal mapping.
- Separate material assignments for skin, sclera/cornea/iris, teeth, tongue, hair, and clothing.
- A neutral facial pose and a documented coordinate/scale convention.
- Facial blendshapes with POSITION and NORMAL deltas, or a documented equivalent facial rig.
- Facial target names mapped to a versioned Vox facial-control schema.
- Optional skeletal joints, inverse bind matrices, weights, and animation clips for head/body motion.
- LODs and texture resolutions suitable for mobile devices.
- Source, creator, license, redistribution/commercial-use permission, and attribution metadata.

Minimum facial controls:
- jaw open / jaw forward
- mouth close, funnel, pucker, left/right smile, left/right stretch
- upper/lower lip raise and lower
- left/right blink, squint, wide eyes
- inner/outer brow raise and brow lower
- cheek raise, nose sneer
- visemes: SIL, PP, FF, TH, DD, KK, CH, SS, NN, RR, AA, EE, IH, OH, OU

## Rendering strategy

The current custom OpenGL ES renderer is a prototype. It supports static mesh rendering, base-color textures, CPU-applied facial POSITION/NORMAL morph deltas, and CPU linear-blend skinning in the bind pose. The renderer applies morphs first and skinning second, then uploads deformed positions/normals for both preview and the shared video-export renderer. It does not yet parse or play glTF animation clips, animate intermediary non-joint nodes, sample normal maps, or consume full metallic-roughness texture channels.

Recommended direction:
1. Preserve the current renderer as a fallback while building a separate renderer integration branch.
2. Evaluate Google's Filament Android runtime and gltfio with a version set that is mutually compatible with the project's Android Gradle Plugin, Kotlin, Android SDK, and build environment.
3. Validate GLB loading, material maps, tone mapping, skin shading, eyes, hair transparency/alpha modes, morph target animation, skeletal skinning, lifecycle, and export rendering.
4. Use a single renderer abstraction so preview and video export share the same scene/material/animation evaluation.
5. Do not switch the production default until release build, supported-device tests, memory, thermal behavior, and export parity pass.

Do not add a Filament dependency by guessing a version. Pin runtime and tooling versions together and verify official release metadata before integration.

## AI generation architecture

Android app:
- prompt editor, optional reference image picker, consent/rights confirmation
- generation job UI and status polling
- asset preview, validation summary, download, local library management

Backend:
- authenticated job creation and ownership checks
- provider adapter interface; provider keys stored as server secrets
- input validation, upload limits, rate limits, quotas, abuse prevention
- job queue/status store, retry policy, timeout handling
- output validation: file type, GLB structure, size, mesh/texture limits, required rig targets, malware-safe storage
- signed, expiring download URLs
- deletion and retention controls for source images and generated files
- usage accounting and cost ceilings

Provider adapter contract:
- submit(prompt, optionalReference, requestedQuality)
- getStatus(jobId)
- fetchResult(jobId)
- cancel(jobId) when supported

No provider is selected in this phase. A provider must be selected only after its output demonstrably satisfies the 3D asset contract and its commercial terms/costs are confirmed.

## Phased implementation

### Phase 0 — Asset and renderer gate
- Keep the current avatar labelled as a prototype, not photorealistic.
- Establish asset validation and metadata schema.
- Acquire or commission one fully licensed production-quality test character.
- Validate that the test character has the required blendshapes and textures in an external glTF validator.

### Phase 1 — Runtime foundation
- Add a renderer abstraction and GLB asset validation.
- Complete glTF animation import/playback, including node hierarchy evaluation, and verify the existing bind-pose CPU skinning integration.
- Add normal/roughness/occlusion texture support and correct tangent-space shading.
- Support independent left/right facial controls and robust morph weights.
- Ensure preview/export share animation evaluation.

### Phase 2 — Ready-made library
- Add the first production human characters and metadata.
- Add thumbnail previews, quality/performance labels, and license records.
- Add graceful fallback for unsupported or oversized assets.

### Phase 3 — AI Face Generator
- Select a provider only after proof-of-output and commercial review.
- Implement backend provider adapter, secure jobs, validation, quotas, and asset delivery.
- Add text-to-character first; add reference-assisted generation only after privacy, consent, and deletion flows are implemented.
- Keep user informed if a provider returns only a portrait or a non-rigged mesh; do not silently treat it as a complete avatar.

### Phase 4 — Quality and release
- Test on low-, mid-, and high-tier Android devices.
- Measure frame time, memory, thermal throttling, load time, and export time.
- Test lip-sync, blink, expression transitions, head movement, and long recordings.
- Compare preview and exported MP4 frame output.
- Complete license, privacy, security, and store-policy review.

## Acceptance criteria

A character is marked Production Ready only when:
1. No visibly disconnected or floating facial parts at neutral pose or during speech.
2. Eyes have believable iris, pupil, cornea highlights, and independent eyelid motion.
3. Mouth opening reveals correctly placed teeth and tongue; jaw motion does not detach lips/chin.
4. Facial controls work independently and blend without severe mesh distortion.
5. Skin and hair respond plausibly to lighting; texture maps are actually consumed by the renderer.
6. Speech animation uses timestamped visemes/phonemes when available, with smoothing and coarticulation.
7. Preview and exported video use the same character, rig state, lighting, and camera evaluation.
8. Asset license explicitly permits the intended commercial redistribution.
9. Device performance and memory budgets are met on the declared minimum device tier.

## Current limitations and truth-in-labeling

The bundled `asian_woman.glb` is only a small static mesh and is not a production digital-human model. The current importer supports embedded GLB geometry, embedded images, base-color material data, and facial morph POSITION/NORMAL deltas. External glTF resource files are rejected with an explicit error (use a self-contained GLB). Skeletal skinning data is imported and the CPU linear-blend skinning path is connected to the renderer in bind pose; this has not yet been build- or device-verified. Runtime glTF animation clips and animated joint poses are not implemented. Normal-map sampling and full PBR texture channels are also not currently supported. The existing audio analyzer estimates visemes from audio bands; it is not phoneme-level alignment.

Do not claim that AI Face Generator, photorealistic characters, skeletal animation, or Filament rendering are implemented until their respective phases have been completed and tested.
