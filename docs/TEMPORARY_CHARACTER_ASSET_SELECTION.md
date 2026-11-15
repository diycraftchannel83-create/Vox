# Temporary Character Asset Selection

## Selected candidate

**Woman Base Mesh — Grab3D**
- Source page: https://grab3d.com/models/characters/woman-base-character/
- Selected download variant: Rigged GLB (the source page lists a separate rigged GLB, approximately 12 MB)
- License stated by publisher: CC0 1.0
- Commercial use: allowed
- Attribution: not required
- Publisher description: adult female base mesh, relaxed A-pose, taupe sports top, charcoal shorts, bare feet; PBR base-color, roughness, metallic and normal maps; rigged version includes walk and run animation.
- Publisher's stated full-quality geometry: 305,702 triangles; the page lists a separate lightweight option.

## Why selected

This is a better temporary Android integration candidate than an unrigged or unclothed base mesh: it is a clothed adult female character, has PBR textures, offers a dedicated rigged GLB, and the source page explicitly states CC0 and commercial use.

## Important limitations

This is **not** the final VOX character and does not match the reference woman's hijab, blazer, long skirt or shoes. It is a temporary generic character for validating the app's import/render/animation path.

The source page does not document facial blendshape targets or a facial performance rig. Do not assume facial rig or lip-sync support. Do not label it photorealistic Indonesian digital human.

## Acquisition and integration status

- Source and license page reviewed: yes.
- Binary downloaded and inspected: no.
- Added to the Android assets directory: no.
- GLB structural validator passed: no.
- Android runtime compatibility verified: no.

The binary download endpoint returned a GLB content type that this environment cannot retrieve. Do not create a placeholder file or fabricate a license manifest. Obtain the actual rigged GLB from the source page, then inspect its embedded metadata and structure before adding it to the app.

## Required next steps after binary acquisition

1. Download the **Rigged** variant and select **GLB** on the source page.
2. Keep a copy of the publisher's CC0 license and origin metadata with the asset.
3. Inspect the actual GLB header, embedded buffers/textures, node hierarchy, skins, animation clips, and material slots.
4. Add a truthful adjacent license manifest recording the source URL and CC0 1.0.
5. Run the repository's asset gate only after checking whether the validator's facial-rig requirements are appropriate for this temporary fallback. A missing facial rig must remain a documented limitation, not be disguised.
6. Integrate as a clearly named temporary fallback, not as the final production avatar.

## License references

- Model page: https://grab3d.com/models/characters/woman-base-character/
- CC0 1.0: https://creativecommons.org/publicdomain/zero/1.0/
