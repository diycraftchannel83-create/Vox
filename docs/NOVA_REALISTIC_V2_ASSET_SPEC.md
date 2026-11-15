# Nova Realistic V2 — Avatar Asset Specification

This document defines the model contract for the next realistic Nova avatar. The current bundled `asian_woman.glb` is a small static mesh and is not a production digital-human asset. Do not describe it as photorealistic or facially rigged.

## Required delivery

- One binary glTF 2.0 `.glb` file with all geometry, textures, and buffers embedded.
- A realistic adult human female head and upper torso with anatomically integrated eyes, eyelids, nose, lips, ears, teeth, and hair.
- UV-mapped 2K base-color texture at minimum; 4K is preferred for close-up export.
- Tangent-space normal map and roughness map. The current custom renderer only samples the base-color texture, so these maps require the planned PBR renderer upgrade before they can affect the final image.
- Separate meshes/materials for skin, eyes, teeth, hair, and clothing.
- Facial blendshapes on the head mesh. Include both POSITION and NORMAL deltas for each target.
- Keep the head mesh in a neutral, closed-mouth expression at zero weights.

## Blendshape names

Use these names (case-insensitive matching) so the current facial rig can drive the targets:

| Target name | Intended action |
|---|---|
| `jawOpen` or `mouthOpen` | Lower jaw / open mouth |
| `mouthSmileLeft`, `mouthSmileRight` | Natural smile |
| `mouthPucker` or `mouthFunnel` | Rounded lips |
| `mouthPress` or `mouthClose` | Lip closure |
| `upperLipRaise` | Raise upper lip |
| `eyeBlinkLeft`, `eyeBlinkRight` | Independent eyelid blink |
| `browInnerUp`, `browOuterUpLeft`, `browOuterUpRight` | Brow expression |
| `viseme_aa`, `viseme_ee`, `viseme_ih`, `viseme_oh`, `viseme_ou` | Vowel mouth shapes |
| `viseme_pp`, `viseme_ff`, `viseme_ss`, `viseme_ch`, `viseme_dd`, `viseme_nn`, `viseme_rr` | Consonant mouth shapes |

For each morph target, export both position and normal deltas. Name the targets in glTF mesh extras as `targetNames`, matching the primitive target order.

## Current engine boundaries

- The GLB importer reads embedded GLB buffers and embedded images. A standalone .gltf file that depends on external .bin or image files is not self-contained and is not a suitable delivery format yet.
- The current renderer applies facial position and normal morph deltas on the CPU and uploads dynamic vertex buffers.
- The current renderer applies CPU facial morph deltas followed by CPU linear-blend skinning in bind pose, and uploads the deformed mesh for preview/export. This integration has not yet been build- or device-verified. glTF animation clips, animated joint poses, normal-map sampling, and full metallic-roughness texture channels are not yet supported.
- The current audio analyzer derives approximate visemes from audio frequency bands. It is not phoneme-level speech recognition; accurate lip sync will require phoneme/viseme timestamps from a speech-alignment or TTS source.

## Acceptance checklist

1. Neutral face is anatomically complete and has no disconnected facial features.
2. Base-color texture is embedded and renders without missing-image placeholders.
3. Each listed facial target moves only its intended region and has matching normal deltas.
4. Jaw opening does not detach the lips, teeth, or chin.
5. Left and right blink targets work independently.
6. The model remains within mobile memory and frame-time budgets.
7. Preview and MP4 export use the same model and facial-rig timeline.

## Asset licensing

The model supplier must provide redistribution and commercial-use rights suitable for inclusion in a public Android application. Record the source, license, attribution requirements, and any restrictions alongside the asset.
