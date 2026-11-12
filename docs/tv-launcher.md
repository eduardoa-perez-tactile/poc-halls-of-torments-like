# Android TV launcher artwork

The launcher now uses an original crypt-portal crest with two embers, one gold and one muted teal, representing the two-player party. Its simple silhouette stays readable at launcher size and uses the same slate/brass palette as the lobby. The banner pairs this crest with the full **COUCH CRYPT** wordmark. It is original code-native vector/raster artwork, not an asset from the PVGames reference pack.

## Resources

- `android:icon` points to `@mipmap/ic_launcher`. Android 8+ uses the foreground vector and solid background in `mipmap-anydpi-v26/ic_launcher.xml`.
- The adaptive foreground uses a 108 x 108 viewport. All crest artwork is within x=22..86, y=22..88, inside the central 72 x 72 safe region (18..90). No external icon border, extra status badge, or themed monochrome layer is supplied.
- Legacy square icon PNGs are supplied at mdpi 80 x 80, hdpi 120 x 120, xhdpi 160 x 160, xxhdpi 240 x 240, and xxxhdpi 320 x 320.
- `android:banner` points to `@mipmap/tv_banner`. Opaque 16:9 PNG banners are supplied at 160 x 90, 240 x 135, **320 x 180**, 480 x 270, and 640 x 360. The title is baked into every image. The current app supports English; additional app-name localizations will need their own banners.
- `android:appCategory="game"` identifies the application category without requiring a physical gamepad. Existing Leanback and non-touchscreen declarations are preserved.

Launcher resources are Android resources rather than LibGDX runtime textures. The largest banner is 640 x 360; the complete PNG set is approximately 135 KiB on disk. There are no network image loads or image-generation calls at runtime.

## Reproduce

From the repository root, using JDK 21:

```sh
java -Djava.awt.headless=true scripts/GenerateLauncherArt.java
```

The source draws the same crest as the adaptive foreground and rasterizes the wordmark with the bundled Cinzel font. It creates the ten PNG density resources directly; no external image libraries are required. Keep the source crest and foreground vector aligned when changing the mark.

## Checks performed

Generated all ten files with the dimensions above, checked PNG resource sizes, and visually inspected the actual 320 x 180 banner and 160 x 160 square icon. The combined APK links and installs successfully. The banner was also visibly verified in the emulator launcher's Installed Games row, with the full wordmark readable. This is an icon/banner pass, not a full Google Play TV-quality certification.

## Official guidance consulted

- [TV app icon design guidelines](https://developer.android.com/design/ui/tv/guides/system/tv-app-icon-guidelines): required icon and banner, recommended adaptive icon, density sizes, safe region, full-logo banner treatment, and avoiding outer borders.
- [Create and run a TV app](https://developer.android.com/training/tv/get-started/create): manifest placement, 320 x 180 xhdpi banner with embedded localized title, Leanback launcher, and touchscreen declaration.
