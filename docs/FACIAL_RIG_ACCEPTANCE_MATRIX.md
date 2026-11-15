# VOX Facial Rig and Digital-Human Acceptance Matrix

This matrix is the human-review companion to the automated GLB structural gate. A passing script is necessary but never sufficient.

## Review setup

- Use the final GLB, not a render, screenshot, or concept image.
- Review at neutral lighting and at close-up scale (face fills at least half the viewport).
- Record asset SHA-256, Blender/DCC version, exporter version, device model, Android version, and renderer revision.
- Review the same asset in the Android preview and exported MP4.
- Confirm that the license manifest corresponds to the exact asset and that the underlying contract/license grants commercial redistribution and modification.

## Face and anatomy

| Check | Required result | Reject when |
|---|---|---|
| Neutral face | Adult Indonesian woman appearance; natural facial proportions and asymmetry | Doll-like, mannequin-like, generic smooth mask |
| Eyes | Separate eyeballs, eyelids, tear line, iris/pupil and wet highlights | Painted-on eyes, clipping, dead/glassy gaze |
| Mouth | Separate lips, oral cavity, teeth and tongue | Flat mouth texture, missing inner mouth, teeth intersecting lips |
| Skin | Fine pore and roughness variation with subtle color variation | Uniform plastic sheen, waxy surface, obvious texture stretching |
| Hair/hijab | Clean silhouette, stable geometry, believable folds and material response | Floating geometry, holes, severe clipping or opaque card artifacts |
| Neck/body | Plausible transitions and proportions in full-body view | Detached head, visible seams, broken shoulders or joints |

## Facial performance sequence

Run each control independently, then in combinations. Capture front and three-quarter views.

1. Blink left, blink right, and blink both; eyelids must close without gaps or eyeball clipping.
2. Look up, down, left and right; eyeballs must remain seated and eyelids must follow naturally.
3. Raise inner brows, lower each brow, and combine asymmetric brow poses.
4. Jaw open, mouth close, mouth funnel and pucker; teeth and tongue must remain anatomically attached.
5. Smile and frown left/right; cheek and nasolabial regions must deform without tearing.
6. Exercise viseme AA, EE, IH, OH and OU in sequence and in rapid alternation.
7. Combine jaw, lips, cheeks and brows to detect corrective-shape conflicts.
8. Return every control to zero; the face must return exactly to its neutral pose.

Reject detached vertices, exploding geometry, severe volume loss, mirrored asymmetry errors, and any facial target that moves unrelated regions.

## Body rig and animation

- Validate neutral/rest pose, joint hierarchy, inverse bind matrices and normalized weights.
- Test head turn, neck bend, shoulder raise, elbow bend, wrist rotation, hip, knee and ankle articulation.
- Inspect weight painting at shoulders, elbows, wrists, hips, knees and neck.
- Run idle animation for at least five minutes; check jitter, foot sliding, pose drift and frame pacing.
- Verify that animation sampling uses glTF timestamps and interpolation modes; do not assume fixed frame rates.

## Android and export parity

- Preview and exported video must use the same camera framing, aspect ratio, material response, animation time, facial weights and lighting intent.
- Test portrait and landscape output if both are supported by the product.
- Verify audio/video synchronization and lip-sync timing at the beginning, middle and end of a long sample.
- Test sustained playback on a low/mid-range Android device and record FPS, memory, thermal behavior and dropped frames.
- Confirm lifecycle behavior after pause/resume, background/foreground, rotation and interrupted export.

## Sign-off record

Do not mark the asset production-ready until each section has an evidence link, reviewer, date, device/build identifier and pass/fail result. A missing result is pending, not pass.
