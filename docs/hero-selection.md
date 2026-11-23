# Portrait-led hero selection

Approved on 23 November 2026 from the generated two-card proposal. The TV chooser uses two head-and-shoulders portraits, class roles and one short playstyle description. Left/right browses; OK on a card confirms it. Down opens the action row at Hero Info. The Choose action confirms the browsed hero; Back cancels without changing the selected hero. Hero Info retains the complete biography/stat/skill reference and restores the preview and focus when closed. Controller removal remains available for reserved slots; active run participants retain their existing management behavior.

Lobby player cards and the existing Hero Info identity use the exact same face regions. Existing crypt layers, QR joining, phone controls, shared menus and gameplay are unchanged by this milestone. All text, actions and focus states are live Scene2D UI; the concept screenshot is not used as an interface.

## Original art and resources

The built-in ImageGen tool produced the new portrait pair, using the approved concept plus the canonical Warden and Ranger front references. Warden retains the plain slanted steel helmet, narrow visor, red mantle and round gold fasteners. Ranger retains the teal hood, shadowed face, brown leather and gold clasp. No third-party character artwork was downloaded.

- Intact generated master: `art-source/hero-portraits-v1/master.png`, 1774x887; original tool file `exec-723c7988-346e-4f25-8acf-4ae54f752267.png`.
- Exact generation prompt: `art-source/hero-portraits-v1/prompt.txt`.
- Built-in ImageGen background extraction: `art-source/hero-portraits-v1/transparent.png`, original tool file `exec-30a91f75-78b9-4976-9380-91b5962447e8.png`. Exact edit prompt: `art-source/hero-portraits-v1/transparency-prompt.txt`. This intact RGBA master supplies the runtime page. A two-quad bottom fade in the shared drawable blends the cropped shoulders into each panel, with a fixed vertex array and no extra texture or per-frame allocation.
- Input concept: `exec-112b65ea-be35-4afe-8b0c-97a2648fdc3c.png`, approved in this chat. Canonical identity inputs: `.run/art-exploration/warden-pilot-v1/source-front/approved-front.png` and `.run/art-exploration/weekend/ranger/references/front-approved.png`, belonging to the gameplay provenance documented in `gameplay-art-integration.md`.
- `scripts/BakeRuntimePortrait.py` only resizes offline. It exports the new face page at 768x384 RGBA and the existing full-body painting at 640x320 RGBA. Existing full-body art and normalized crop coordinates are preserved; its source height remains above the largest existing 318px draw. Source masters are unchanged.
- The two file-backed managed pages total **1.90625 MiB GPU**, versus 2 MiB for the previous full-body page. Neither retains a decoded CPU Pixmap. No new font, framebuffer, animation loop or render-time layout rebuilding is required. Portrait focus uses existing shared resources.

## Focused verification

JDK 21 offline core/desktop compilation and Android debug packaging passed. Brief native checks on the existing `Television_1080p` ARM64 API 33 emulator covered both hero cards, confirming Ranger, canceling back to the previously selected hero, both player slots, Hero Info and exact preview/focus restoration, reserved-slot removal, and Player II selection/Info from Pause → Invite. One heartbeat-only synthetic phone exercised the connected/reserved states and a short launch-to-pause transition; it was closed afterward and the app was restored to an empty lobby. No gameplay playthrough or broad regression was performed.

The final native accounting includes **33,319,756 texture bytes (31.7762 MiB)** with the live QR, leaving **234,676 bytes** below the 32 MiB ceiling. Retained decoded Pixmaps remain **20,245,324 bytes**; neither new portrait loading nor the two-quad fade adds retained decoded pixels. A short empty-lobby window reported 21 UI draws and CPU submission p95 7.03ms, with frame interval p95 19.44ms; this exceeds the strict 16.7ms display target and does not establish physical-TV 60 FPS. Timing varies with emulator/host load. This UI change does not establish gameplay balance.

Ignored local evidence is under `.run/hero-selection-review/`. The emulator and pre-existing development relay remain running for review; no physical device was modified and temporary synthetic clients were stopped.
