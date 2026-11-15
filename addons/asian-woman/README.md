# Premade Add-on: Asian Woman

This addon registers a downloadable/importable Asian woman avatar asset for Vox.

## Candidate source

**Rigged Standing Asian Female Character Idle Pose** by florah  
Source: https://sketchfab.com/3d-models/rigged-standing-asian-female-character-idle-pose-b31b024797194829af4637fbfb6838b7

The source listing describes a full-body Asian female character with a complete skeleton rig, PBR textures, and a neutral standing pose. It is published under **Creative Commons Attribution (CC BY)**. Before bundling or redistributing the downloaded model, preserve the author's required attribution and include the exact license notice from the downloaded asset page/archive.

## Install

1. Download the model from its source page using the creator's download control.
2. Export/download as a single **glTF 2.0 Binary (.glb)** file with embedded textures.
3. Save it as `asian_woman.glb`.
4. In Vox, open **3D Avatars & Stage → Import Custom 3D Avatar** and select the file.

Vox currently imports GLB through `AvatarImporter` and `GltfModelParser`. The importer copies the selected model to app-private storage and creates an imported avatar entry.

## Compatibility notes

- Use GLB 2.0 with embedded buffer and image data.
- Prefer a neutral A-pose or relaxed idle pose.
- The current Vox GLTF parser reads mesh positions, normals, UVs, materials, and position morph targets. It does **not** currently implement glTF skinning/joint weights or animation playback. Therefore, although the source model is rigged, Vox will currently render its static mesh only.
- The current audio-driven face controller requires compatible facial morph targets. A body rig alone does not provide viseme/lipsync.
- For actual talking animation, prepare a derivative with supported facial morph targets (for example jawOpen, mouthClose, mouthFunnel, mouthSmile, eyeBlink) and extend the importer/renderer to support glTF skinning and animation before describing the addon as fully animated.

## Attribution

When redistributing the asset, include the creator's name, the source URL, and the CC BY license/version and any required changes notice. Do not redistribute the downloaded GLB until the source download page's exact license terms have been verified and complied with.
