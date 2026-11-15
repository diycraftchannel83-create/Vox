# Face Visibility and Mouth Motion Update

The current priority is a clearly visible face and readable mouth articulation in both the live viewport and rendered MP4. Photorealistic skin and high-end character assets are intentionally deferred.

## Implemented in the current work branch

- Default camera zoom is increased so the head occupies more of the frame in both preview and export (both instantiate the same renderer defaults).
- Jaw and lower lip now rotate downward when the jaw opens. The earlier positive X rotation moved the chin in the wrong direction for this coordinate system.
- The mouth cavity expands vertically as the jaw opens, making an open mouth visibly dark rather than looking like a flat line.
- Lower teeth are attached to the jaw part so they follow mandible motion; upper teeth remain fixed to the upper head.
- The static-import fallback uses ordered smoothstep edges rather than reversed edges, avoiding undefined GLSL behavior.
- TTS scripts have a text-derived viseme timing estimate; Arabic, Japanese kana and Korean Hangul now have a coarse non-Latin fallback so they do not produce a completely silent mouth animation.

## Important limits

The multilingual mapping is approximate and is not phonetic analysis. The text-duration estimator is not forced alignment. Imported audio and live microphone continue to use acoustic feature-based estimates. Detailed facial blendshapes and a production-grade character are still separate work.

## Validation still required

- Inspect neutral, closed-lip, open-vowel, rounded-vowel, and bilabial frames on a real Android device.
- Confirm upper/lower teeth do not intersect or float during the full jaw range.
- Confirm the mouth cavity remains behind the lips and does not expose gaps at extreme jaw opening.
- Compare live viewport and MP4 at identical timestamps.
- Only after face and mouth changes are complete, run Gradle build, unit tests, and device export checks.
