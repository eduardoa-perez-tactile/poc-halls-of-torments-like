# Android TV launcher artwork

The launcher uses an original painted crypt guardian: a monumental weathered greathelm, fierce amber eyes, warm firelight, and cold teal rim light. This replaces the earlier flat portal crest. The 16:9 banner combines that artwork with a crisp **COUCH CRYPT** wordmark rasterized from the bundled Cinzel font. It is original generated illustration, not a PVGames asset or a named franchise character.

The user approved this guardian icon and matching banner on 2026-11-12.

## Android resources

- `android:icon` remains `@mipmap/ic_launcher`. Android 8+ uses a painted 432 x 432 raster foreground and solid charcoal background through `mipmap-anydpi-v26/ic_launcher.xml`.
- The adaptive layer fills its bounds. The eyes and essential central face were composed inside the central 60% of the master, within the 72 x 72 safe region of a 108 x 108 adaptive layer. Peripheral shoulders/crown points can be masked. No outer frame, text, status badge, or themed monochrome layer is added to the square icon.
- Legacy square icon PNGs: mdpi 80 x 80, hdpi 120 x 120, xhdpi **160 x 160**, xxhdpi 240 x 240, and xxxhdpi 320 x 320.
- `android:banner` remains `@mipmap/tv_banner`. Opaque title-banner PNGs: 160 x 90, 240 x 135, **320 x 180**, 480 x 270, and 640 x 360. The English app title is baked into every density resource. Additional supported app-name languages will need corresponding localized banners.
- `android:appCategory="game"`, Leanback launcher, non-touchscreen support, and the existing singleTask launch mode are preserved.

The full 1254 x 1254 source is stored at `docs/art/launcher-guardian.png`, outside the LibGDX asset directory. It is not packaged as a game texture. Only the bounded Android launcher resources are installed; the largest painted adaptive resource is 432 x 432 and the largest banner is 640 x 360. Artwork is not generated or downloaded at runtime.

## Reproduce Android densities

From the repository root, using JDK 21:

```sh
java -Djava.awt.headless=true scripts/GenerateLauncherArt.java
```

This build-time script scales the already generated master and composites the title in code. It does not generate new artwork or rely on AI lettering. It produces ten density PNGs and the adaptive foreground using only JDK image/font APIs.

## Source artwork and exact prompt

Created on 2026-11-12 with the built-in image-generation tool. No CLI/API fallback was used.

Use case: stylized-concept. Asset type: square premium dark-fantasy dungeon-survival GAME APP ICON master artwork. Create a genuinely EPIC original painted game-cover illustration, not a vector logo or simple geometric mark. Subject: extreme close-up of a monumental ancient crypt guardian knight, weathered blackened iron greathelm with a strong jagged crown-like central ridge, massive iron faceplate and two unmistakable luminous amber eye slits. Camera just below eye level, face looking straight at viewer with slight three-quarter turn, helm and upper shoulders form one bold dominant silhouette. Aged brass inlays and chipped stone dust, battle scars and thick metal. Cinematic warm torchlight from lower-left contrasts with crisp cold teal rim light at upper-right. Faint amber embers, deep midnight crypt shadows behind; restrained mist. Premium painterly realistic 2D dark-fantasy cover art with rich handcrafted material detail, powerful focal lighting, frightening grandeur, extraordinary finish. Composition: square, centered helmet occupies central 65 percent of canvas and eyes near upper-middle. Keep critical eye/face detail entirely within central 60 percent width and height so circular Android launcher masks preserve the face; outer shoulders and mist can bleed to edges. Strong contrast and two bright eyes must read at 160 pixels. Background full bleed opaque dark slate, no border, no frame. No lettering, text, logo, tiny figures, sword clutter, badges, extra faces, UI, or watermarks. Original design, do not reproduce recognizable named IP.

## Focused checks

The actual 160 x 160 square icon and 320 x 180 banner were visually inspected after rendering, including eye contrast and title legibility. All eleven PNG sizes and XML syntax were checked. APK linking and the actual TV launcher presentation are verified by the main implementation task. These checks do not imply complete Google Play TV-quality certification or testing of every vendor's launcher mask.

## Official guidance consulted

- [TV app icon design guidelines](https://developer.android.com/design/ui/tv/guides/system/tv-app-icon-guidelines): icon/banner resources, adaptive layers and safe region, density sizes, title-banner treatment, and avoiding outer borders.
- [Create and run a TV app](https://developer.android.com/training/tv/get-started/create): manifest references, 320 x 180 xhdpi banner with embedded localized title, Leanback launcher, and touchscreen declaration.
