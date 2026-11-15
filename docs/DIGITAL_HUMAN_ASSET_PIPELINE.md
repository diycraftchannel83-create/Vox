# Photorealistic Digital Human Asset Pipeline

## Non-negotiable delivery

Ship a genuinely authored or licensed human character, not a procedural primitive, mannequin, stock placeholder, or thumbnail. The current bundled prototype meshes are not production character assets.

The production asset must be a self-contained glTF 2.0 binary (.glb) containing:
- Anatomically plausible adult face topology, separate eyes/eyelids, lips, teeth and tongue.
- UVs and embedded PBR base-color, tangent-space normal, and metallic-roughness maps.
- Realistic skin detail (pore/roughness variation and subtle color variation; avoid plastic specular).
- Groomed hair geometry/cards with suitable material and alpha mode.
- Documented humanoid skeleton with normalized skin weights.
- Facial blendshapes with symmetric/asymmetric controls and the names enforced by scripts/check_digital_human_glb.py.
- At least one idle animation and facial performance animation.
- Embedded buffers and textures; no external file dependencies.

## Authoring and export

1. Author or commission in Blender or a compatible DCC tool. Record artist, source, contract/license, modification rights, and commercial redistribution rights.
2. Model production face topology; shader-only facial tricks are not a substitute for facial geometry.
3. Create UVs and bake skin detail to normal maps. Keep color and data maps in their correct color spaces.
4. Rig body and face. Validate skin weights, joint hierarchy, neutral pose, blink closure, jaw range, and lip closure.
5. Create named ARKit-compatible facial shapes or explicit aliases required by the app.
6. Export glTF 2.0 GLB with embedded textures, animations, skins, and morph targets.
7. Add a same-basename .license.json containing asset_name, author, source_url, license, attribution, commercial_redistribution=true, and modification_allowed=true.
8. Run: python scripts/check_digital_human_glb.py path/to/production_human.glb
9. Complete close-up visual review, preview/export parity, and performance testing on representative Android devices.

## Runtime integration requirements

The current custom OpenGL renderer is not yet a complete glTF runtime. Before release it must correctly support base-color, normal, metallic-roughness, occlusion and emissive texture slots; tangent-space normal mapping; alpha modes; sRGB/linear handling; glTF node animation sampling; joint animation; morph weights; and preview/export parity. Unsupported extensions or animation channels must not be silently ignored.

If the custom renderer cannot meet conformance and performance requirements, evaluate a maintained glTF runtime such as Filament behind a renderer interface. Verify licensing, APK size, device compatibility, and export integration before adoption.

## Acceptance gates

- Structural script passes and rights manifest is reviewed.
- Human reviewer approves face, skin, hair, teeth, eyes, and expressions at 1:1 close-up.
- Facial controls pass a phoneme/viseme sequence without clipping or detached geometry.
- Animation clips and skeletal skinning work at runtime.
- Preview and export match in framing, lighting, expression, and timing.
- Sustained playback meets agreed FPS and thermal budgets.
- Legal/provenance and privacy reviews are signed off.

A structural pass is not proof of realism, license validity, or production readiness. This script cannot create the final asset.
