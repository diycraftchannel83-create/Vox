# Premade Add-on: Asian Woman

Vox now includes the actual binary GLB asset at `asian_woman.glb`.

## Asset details

- Format: glTF 2.0 Binary (.glb)
- Type: original stylized low-poly full-body female avatar
- Materials: per-vertex colors; no external texture files
- License: CC0-1.0 (original geometry generated for this project)
- File: `addons/asian-woman/asian_woman.glb`

## Import into Vox

1. Open **3D Avatars & Stage**.
2. Choose **Import Custom 3D Avatar**.
3. Select `asian_woman.glb` from the repository checkout or copy it to the device first.
4. Vox copies the selected GLB into app-private avatar storage.

## Current compatibility

The asset contains static mesh geometry only. The current `GltfModelParser` can parse its positions and vertex colors. It does not yet implement glTF skinning (`JOINTS_0` / `WEIGHTS_0`) or animation playback.

The model does not include facial morph targets, so it is not lipsync-ready. To enable talking animation, a future asset revision needs facial viseme morph targets and Vox needs corresponding parser/renderer support.

This is a stylized low-poly starter asset, not a photorealistic character scan.
