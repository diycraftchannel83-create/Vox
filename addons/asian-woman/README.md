# Premade Add-on: Asian Woman

Vox now includes the GLB as both an add-on asset and a bundled Android app asset at `app/src/main/assets/avatars/asian_woman.glb`.

## Asset details

- Format: glTF 2.0 Binary (.glb)
- Type: original stylized low-poly full-body female avatar
- Materials: per-vertex colors; no external texture files
- License: CC0-1.0 (original geometry generated for this project)
- File: `addons/asian-woman/asian_woman.glb`

## Use in Vox

1. Open **Avatars**.
2. Select **Asian Woman** from the built-in avatar roster.
3. The app copies the bundled GLB into app-private storage on first load. No manual import is required.

The same file remains available for manual import through **Import Custom 3D Avatar**.

## Current compatibility

The asset contains static mesh geometry only. The current `GltfModelParser` parses the static geometry, but does not yet pass `COLOR_0` vertex colors through to the renderer. It does not implement glTF skinning (`JOINTS_0` / `WEIGHTS_0`) or animation playback.

The model does not include facial morph targets, so it is not lipsync-ready. To enable talking animation, a future asset revision needs facial viseme morph targets and Vox needs corresponding parser/renderer support.

This is a stylized low-poly starter asset, not a photorealistic character scan.
