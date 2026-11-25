# Mourning Bell visual proposal

On 2026-11-25 the user preferred the revised nonhuman crypt predator concept (`preferred-concept.png`), responding "much better". The earlier literal floating bronze bell was rejected as a prop rather than an enemy.

Direction: a compact hovering creature with a cracked bone shell, a single amber eye and a large fanged maw. Its name refers to its tolling cry. Animation studies explore hover, body tilt/lunge, jaw movement and recoil. The user subsequently authorized implementation at **5:00 elapsed combat time** on 2026-11-25.

Original artwork generated using the built-in image-generation tool. Exact concept prompt: `generation-prompt.txt`. Original generated file: `/Users/eduardoleale/.codex/generated_images/01a212e4-198a-7c31-9f84-a1b13ea4904a/exec-78628206-1940-4edd-84cd-c541c53a67f3.png`.

## Production source

The first production sheet (`rejected-frontal-sheet.png`, prompt `production-prompt.txt`) was explicitly rejected for its frontal camera. It is preserved for provenance and is not used at runtime.

`generated-facing-sheet.png` corrects the camera to an elevated isometric view, using the preferred creature for identity and the existing Brute artwork (`isometric-angle-reference.png`, originally `.run/art-exploration/weekend/brute/raw/SE-alpha-v2.png`) for projection. Exact prompt: `isometric-production-prompt.txt`. Generated original: `/Users/eduardoleale/.codex/generated_images/01a212e4-198a-7c31-9f84-a1b13ea4904a/exec-1442f362-a395-4f2a-9e9a-2e05aeb234d7.png`.

The source has four authored diagonal facings SW / SE / NE / NW, idle above and open-jaw action below. Back views show the shell and rear hide without a face. No external art pack or purchased artwork is used. The source sheet remains distinct from executable gameplay evidence.

## Runtime export and resources

Run `python3 scripts/BakeMourningBell.py` after the existing gameplay, attack,
border, floor, Grave King and painted-corner stages. Use `--check` to verify the
final Bell export. Older stage bakers assume their own append suffix; reproduce
them in stage order before adding the Bell.

Eight normalized frames occupy one file-backed managed 256×256 RGBA page
(`gameplay-mourning-bell.png`, 262,144 GPU bytes). One global scale and shared
per-facing crops retain stable 96×96 logical canvases and (48,96) ground roots.
Two-pixel extruded gutters isolate the frames. The runtime uses a 1.7 body scale
over the 120/96 source scale, approximately 170 world units at its tallest pose.
Original source pixels are retained; offline edge cleanup corrects two saturated
red boundary pixels from the image generator without recoloring interior flesh.
`bake-report.json` records exact source and preserved old-page hashes.

The previous four gameplay pages, frame indices, actor entries, regions and
storage cuts remain unchanged. The new managed page retains no decoded Pixmap.
To retain the combined 32 MiB ceiling, the shared font page loses only its unused
transparent bottom: 1024×768 → 1024×576. Glyph pixels, source coordinates, metrics
and two-pixel gutters remain exact. The 786,432-byte saving more than covers the
new page; maximum combined residency including the 360×360 QR is 32,959,308 bytes
(31.4324 MiB), a net decrease of 524,288 bytes.

`scripts/BakeMourningBellAudio.py` synthesizes the original 0.85-second mono toll
cue (`assets/audio/bell-toll.wav`, 22,050 Hz PCM). It uses no sampled recording.
The lunge reuses the existing dash cue and the encounter reuses the mini-boss
music. No new VFX texture, framebuffer or simulation pool is introduced.
