# VOX Lip-Sync Behavior and Current Limits

## Current behavior

- Built-in Nova/Atlas/Milo/Aura characters use the app's procedural face parts and renderer controls for jaw, upper/lower lip, mouth width, pucker, lip press, eyelids and brows.
- Imported GLB assets with facial morph targets use the target names mapped by `FacialRigState.weightForMorphName`.
- Imported meshes without morph targets use the renderer's coarse automatic mouth deformation. This is a fallback only; it cannot reproduce detailed phonemes on arbitrary topology.
- TTS-generated script audio now uses a deterministic grapheme-timed viseme estimate based on the exact script and decoded audio duration.
- Imported audio and live microphone analysis still use audio-feature classification. FFT energy bands can animate a mouth but cannot reliably recover exact phoneme identity.

## Accuracy statement

The script-timed path is more semantically informed than FFT-only mouth movement, but it is not a forced aligner. Character durations are estimated from text and weighted graphemes; actual pronunciation, pauses, stress, and voice-engine timing can differ. Do not describe this as frame-accurate phoneme alignment.

For precise lip sync, integrate a speech engine that exposes word/phoneme boundary timestamps or run a licensed forced-alignment model. Preserve the audio's sample-rate/timebase and map its timestamps to viseme intervals. A fallback should remain available when alignment data is missing.

## Acceptance checks before release

1. Confirm a visible face and mouth in the actual exported video, not just in the editor preview.
2. Test Indonesian phrases with P/B/M, F/V, T/D/N/L, K/G/NG, S/SH/CH, and A/I/U/E/O vowel transitions.
3. Inspect closed-lip consonants, open vowels, rounded vowels, and silence/pause behavior.
4. Confirm lips do not detach from the face, expose a missing cavity, clip through teeth, or remain open after speech.
5. Compare preview and export at the start, middle and end of speech.
6. Record human-reviewed pass/fail evidence on a real Android device.

## Current non-goals

No claim is made that the procedural face is photorealistic, that the fallback character has an ARKit facial rig, or that FFT analysis alone yields phoneme-accurate lip sync.
