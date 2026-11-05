# Theming: settings-driven Material 3 theme system plus Nothing-style second theme (Compose, 2026)

## 1. Material 3 / M3 Expressive status and version requirements (BOM 2024.12 upgrade)

### Takeaway
As of 2026-11-05 material3 1.4.0 is the latest stable and 1.5.0-rc01 was published 2026-11-04. Many expressive APIs lost their experimental status during the 1.5.0 alphas, so shipping on stable 1.5.0 (or a BOM that maps to it) is the cleanest path. BOM 2024.12 is far behind. The exact BOM-to-material3 mapping was not verified.

### Cited Findings
- Release page: latest stable `androidx.compose.material3:material3:1.4.0`. Latest update 1.5.0-rc01 on Nov 04, 2026 (bug fixes only). beta02 on Oct 21, beta01 on Oct 7, last alpha 1.5.0-alpha29 on Sep 23, 2026. — [AndroidX release notes, compose-material3](https://developer.android.com/jetpack/androidx/releases/compose-material3)
- 1.5.0-alpha18 added `Material3ExpressiveApi`, an annotation that does not require OptIn, and promoted the expressive theme function and `expressiveLightColorScheme`. The fetch rendered the function name as "materialExpressTheme", which is probably a typo for `MaterialExpressiveTheme`. — [AndroidX release notes](https://developer.android.com/jetpack/androidx/releases/compose-material3)
- alpha19 promoted ToggleButtons, expressive menu, FAB, FAB Menu and button APIs to stable. alpha22 promoted FloatingToolbar. alpha23 promoted expressive list items and the flexible TopAppBar/BottomAppBar APIs. — [AndroidX release notes](https://developer.android.com/jetpack/androidx/releases/compose-material3)
- alpha24 updated Compose dependencies in Material 3 to 1.12.0-beta01. Compose libraries raised `compileSdk` to 37.1 at some point, and alpha23 notes removal of a `compileSdk 37` requirement. Details are murky, so check the compileSdk requirement of the chosen version. — [AndroidX release notes](https://developer.android.com/jetpack/androidx/releases/compose-material3)
- Official docs now describe Compose as implementing "Material You and Material 3 Expressive", which complements the Android 16 visual style and supports dynamic color. — [Material 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)
- `MaterialExpressiveTheme` takes optional `colorScheme`, `motionScheme`, `shapes`, `typography` and `content`. — [Compose Multiplatform API reference](https://kotlinlang.org/api/compose-multiplatform/material3/androidx.compose.material3/-material-expressive-theme.html)
- The current docs example for downloadable fonts uses `androidx.compose.ui:ui-text-google-fonts:1.12.1`, which shows the Compose UI line is 1.12.x now. — [Compose fonts docs](https://developer.android.com/develop/ui/compose/text/fonts)
- Conflicting, non-official: one third-party skill page says 1.4.0 exposes almost none of the expressive APIs and that most graduated in 1.5.0-alpha23. A Kotlin Slack thread says tagged experimental APIs were removed in 1.4 release notes. Both are secondary and conflict with each other. — [composables.com](https://composables.com/material3/materialexpressivetheme), [Kotlin Slack](https://slack-chats.kotlinlang.org/t/30118490/in-compose-1-10-0-alpha01-materialexpressivetheme-api-become)
- BOM mapping is per library group, so material3 and its group share one version in a given BOM. The official mapping page is `developer.android.com/develop/ui/compose/bom/bom-mapping`. — [BOM docs](https://developer.android.com/develop/ui/compose/bom), [Kotlin Slack](https://slack-chats.kotlinlang.org/t/34280010/is-compose-bom-2026-08-00-mapped-to-material3-adaptive-adapt)

### Inferences
- BOM 2024.12 maps to material3 around 1.3.1, which has no expressive components and no `MaterialExpressiveTheme`. That figure comes from my background knowledge, not a source here. Basic theming (`ColorScheme`, `Shapes`, `Typography`, dynamic color) works on it without change.
- To get `MaterialExpressiveTheme`, `MotionScheme` and expressive components, move to a 2026 BOM that maps to material3 1.4.x or 1.5.x. For a new theme system, pin the BOM to a release that includes 1.5.0 stable once it ships. Expect needing newer AGP, Kotlin and compileSdk with it.
- Because the Nothing style will use its own design tokens, the app does not depend on expressive components. Upgrading is optional and can be separated from this feature.

### Gaps
- The exact BOM version containing material3 1.5.0 stable was not found. Read the BOM mapping page or the compose-bom POM on maven.google.com.
- What exactly was stable in 1.4.0 about the expressive API could not be confirmed. The 1.4.0 release notes were beyond the fetched portion.

## 2. Dynamic color, seed-color schemes, AMOLED, theme mode, shapes, fonts, density, DataStore, preview, themed icons

### Takeaway
Use `dynamicLightColorScheme/dynamicDarkColorScheme` on API 31+ and MaterialKolor (`rememberDynamicColorScheme`) to build a scheme from a user seed color and palette style, with contrast level and an `isAmoled` flag built in. Shapes, typography and density are plain data in a settings model, persisted to DataStore. Preview works by applying the same theme builder to a mini-preview composable.

### Cited Findings
- Dynamic color is available on Android 12+. Use `dynamicDarkColorScheme(context)` / `dynamicLightColorScheme(context)` when `Build.VERSION.SDK_INT >= S`, else fall back to custom light or dark `ColorScheme`. — [Material 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)
- A M3 theme has color scheme, typography and shapes. The shape scale has five sizes (extra small to extra large) overridable via `Shapes(...)`. The Compose M3 `Typography` class has no default font family parameter, so each `TextStyle` needs a `fontFamily`. — [Material 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)
- A color scheme is based on five key colors, each with a tonal palette of 13 tones. — [Material 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)
- MaterialKolor (jordond): version 6.0.0 in the README, noted as still in beta. Artifact `com.materialkolor:material-kolor-material3:6.0.0`. 5.x users should read the 6.0 migration guide. — [MaterialKolor GitHub](https://github.com/jordond/MaterialKolor)
- API: `DynamicMaterialTheme(seedColor, isDark, animate, content)`, `rememberDynamicColorScheme(seedColor, isDark)` returns a `ColorScheme`, `dynamicColorScheme` for use outside composition, and `DynamicMaterialExpressiveTheme` with `motionScheme`. — [MaterialKolor GitHub](https://github.com/jordond/MaterialKolor)
- `PaletteStyle` includes TonalSpot (default), Vibrant, Expressive, Monochrome. Third-party port lists also Neutral, Rainbow, FruitSalad, Fidelity, Content. — [MaterialKolor GitHub](https://github.com/jordond/MaterialKolor), [Kotlin port listing](https://github.com/Kyant0/m3color)
- `contrastLevel` ranges -1.0 to 1.0 with `Contrast.Medium.value` and `Contrast.High.value` helpers. `isAmoled` makes dark background and surface pure black. `specVersion` defaults to `SPEC_2025`. `ColorSpec.SpecVersion.SPEC_2021` reproduces pre-6.0 colors. Docs for 5.0 recommend SPEC_2025 with PaletteStyle.Expressive. — [MaterialKolor GitHub](https://github.com/jordond/MaterialKolor), [search summary of docs](https://docsearch.algolia.com/mcp/docs/repo/jordond/materialkolor)
- MaterialKolor is MIT, the generated material-color-utilities code is Apache 2.0, and it supports Android, iOS, desktop, JS and Wasm. — [MaterialKolor GitHub](https://github.com/jordond/MaterialKolor)
- Google's Material Theme Builder can export Compose theming code. — [Material 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)
- Custom fonts load from `res/font` via `FontFamily(Font(R.font.x, FontWeight...))`. Variable fonts require API 26+ and are placed in `res/font` with lowercase names, configured by `FontVariation.Settings(...)`. Variable fonts are not supported via downloadable fonts. — [Compose fonts docs](https://developer.android.com/develop/ui/compose/text/fonts)
- Downloadable Google Fonts need `ui-text-google-fonts`, a `GoogleFont.Provider` with GMS certs, and support a fallback chain to a local `R.font` font. Newly added Google fonts can take months to become available through the Android provider and may throw `IllegalStateException`. — [Compose fonts docs](https://developer.android.com/develop/ui/compose/text/fonts)
- Themed icons: introduced in Android 13 (API 33), use a monochrome layer of the adaptive icon (108x108 dp, content inside the 66x66 dp safe zone). Since Android 16 QPR2 the system auto-themes icons for apps without a monochrome layer. — [Adaptive icons docs](https://developer.android.com/develop/ui/views/launch/icon_design_adaptive)

### Inferences
- Suggested settings model (DataStore Preferences or Proto): `themeStyle` (MATERIAL_YOU | NOTHING), `themeMode` (SYSTEM|LIGHT|DARK), `useDynamicColor` (only offered on API 31+), `seedColor` (ARGB Int), `paletteStyle` (enum name string), `contrast` (-1..1 float, or discrete Standard/Medium/High), `amoled` (Boolean), `cornerScale` or radius preset (Sharp/Default/Round), `fontChoice` (enum), `density` (compact/default/comfortable, implemented as a spacing multiplier in a custom CompositionLocal, since Compose has no global density-of-components switch other than overriding `LocalDensity`, which also scales text). Store enums as strings so renames are tolerated.
- Scheme resolution order: Dynamic (API 31+ and enabled) then seed via MaterialKolor, then the static default. AMOLED can be applied to any of these: for dynamic and seed schemes using the library flag, or by copying the scheme with `background`/`surface` = Color.Black (and lowering `surfaceContainer*` roles) in dark mode. Pure black helps OLED power savings; the copying step is my suggestion, not a sourced practice.
- Shapes from a radius setting: build `Shapes(extraSmall = RoundedCornerShape(4*k.dp), ...)`. Corners can be scaled by a factor `k`, with 0 giving a sharp look that also suits the Nothing style.
- Live preview: collect the settings `Flow` in a ViewModel as `StateFlow<ThemeSettings>` and have both the app root theme and a settings-screen mini-preview card call the same `buildColorScheme(settings, isDark)`. Use a nested `MaterialTheme(colorScheme = previewScheme)` around a small sample (button, switch, card). Apply changes immediately and persist with debounce.
- Reading DataStore asynchronously at launch can flash the default theme. Use an initial value from a blocking first read in the splash stage, or `SplashScreen` keep-on-screen condition, to avoid a flash.
- For themed icon, add `<monochrome android:drawable="@drawable/ic_launcher_monochrome"/>` in `mipmap-anydpi-v26/ic_launcher.xml`. This is the documented mechanism (adaptive icon docs), and the system recolours it using the wallpaper, so it does not follow the in-app seed color.

### Gaps
- Exact `rememberDynamicColorScheme` parameter names in 6.0.0 (contrastLevel/isAmoled/style/specVersion) were not verified against the 6.0.0 source.
- HCT and `Scheme` classes in Google's own material-color-utilities (Java/Kotlin) were not fetched. The Kotlin port by Kyant0 (m3color) and MaterialKolor wrap the same algorithm.
- `ColorScheme` contrast API of androidx itself (there is none that I could confirm) was not researched. Android 14+ system contrast (UiModeManager contrast) was not researched.
- 6.0.0 being in beta means API churn risk. Consider pinning 5.x or 6.0.0 exactly.

## 3. UX best practices for theme settings screens

### Takeaway
No authoritative source on theme-settings UX was gathered in this session. Only official Material docs about contrast and accessible roles were found. Practical guidance below is inference.

### Cited Findings
- Dynamic color and the tonal system are designed to meet accessibility contrast standards, and custom components should pair `on-X` roles with `X` roles. — [Material 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)
- MaterialKolor exposes contrast levels from -1.0 to 1.0 with Medium and High presets. — [MaterialKolor GitHub](https://github.com/jordond/MaterialKolor)

### Inferences
- A common pattern: top segmented control for System/Light/Dark, a switch for "Use wallpaper colors" shown only on API 31+, a horizontal or grid swatch row of curated seed colors (each swatch showing the generated primary), a "Custom" swatch opening an HSV or hex picker, a palette style chooser (chips: Tonal Spot, Vibrant, Expressive, Neutral/Monochrome), a contrast selector (Standard/Medium/High), an AMOLED switch only enabled in dark mode, and a live preview card pinned at the top.
- Show each swatch as the output of the real scheme generation rather than the raw seed, so the user sees what they get. Keep contrast of text on swatches readable and mark selection with a check icon, not color alone.
- Include a "Reset to default" action.

### Gaps
- No sourced UX research, Material guidance page on settings, or real app teardown (Pixel Launcher, Tachiyomi forks, Material Files) was retrieved.

## 4. Nothing OS design language and fonts

### Takeaway
Nothing's look is monochrome black/white, dot-matrix type (Ndot), mono-space uppercase labels, hairline borders over shadows and a restrained accent. Its own fonts (Ndot 55/57, Lettera Mono, NType82) are proprietary with restrictive license, so ship open-source substitutes: Doto (OFL, Google Fonts) for dot-matrix display, and Space Mono or JetBrains Mono (OFL) for the mono labels.

### Cited Findings
- Nothing's phones use a transparent design and a retro-futuristic dot-matrix look in the Android skin. Phone (3) added a 489-LED Glyph Matrix rear display. — [Design Milk](https://design-milk.com/the-nothing-phone-3s-glyph-matrix-turns-notifications-into-pixel-art/), [Yanko Design](https://www.yankodesign.com/2025/06/04/nothing-phone-3-kills-the-glyph-interface-for-a-glyph-matrix-but-theres-a-massive-problem/)
- Third-party design-system breakdown of nothing.tech: palette ink `#000000`, canvas `#ffffff`, ink-muted `#585a5a`, hairline `#e5e7eb`. Fonts: LatteraMonoLL for uppercase labels/buttons/nav, NType82 for editorial text (thin headlines at weight 100, body 400), Ndot for product names at 20-40px. Every label is uppercase. Monochrome with no chromatic accents even on hover/focus. Buttons 6px radius, cards 8px, no pills, elevation via hairline borders not shadows. — [shadcn.io Nothing design page](https://www.shadcn.io/design/nothing)
- Same source: Ndot is proprietary with no direct open-source equivalent. Suggested stand-ins: Silkscreen or Press Start 2P for Ndot; JetBrains Mono, IBM Plex Mono or Space Mono (uppercase) for Lettera Mono. — [shadcn.io](https://www.shadcn.io/design/nothing)
- Another brand-guide site says palette is black, white and red with a signature deep red `#B01319` (third-party, unconfirmed). Conflicts with the page above that says Nothing's site has no accent. — [madegooddesigns brand guidelines (search result)](https://madegooddesigns.com/nothing-brand-guidelines/), contradicted by [shadcn.io](https://www.shadcn.io/design/nothing)
- Ndot-57 has tighter dots than Ndot-55; the open GitHub project listing them credits Nothing Tech with "All Rights Reserved". A third-party download site quotes license terms restricting use to Nothing brand materials and barring modification and sharing; unverified text. — [xeji01/nothingfont listing](https://awesome.ecosyste.ms/projects/github.com%2Fxeji01%2Fnothingfont), [onlinewebfonts](https://www.onlinewebfonts.com/download/d7a74ed36ff0603a3e41b6da32c47f03)
- Lettera Mono appears in Nothing's OEM resource list; a font-pairing site describes Lettera as a Lineto typeface, implying third-party licensing. — [awesome_nothing official resources](https://spike0en.github.io/awesome_nothing/docs/official), [maxibestof.one](https://maxibestof.one/typefaces/lettera/pairing/ndot57)
- Nothing OS 3.0 beta reintroduced NDot as an experimental option limited mostly to settings titles. — [techissuestoday](https://techissuestoday.com/nothing-ndot-font-nothing-os-3-0)
- Open-source substitutes:
  - Doto: Google Fonts, OFL-1.1, variable (weight 100-900), monospace dot-matrix display face by Oliver Lalan, on Google Fonts since 2024. Also on Fontsource. — [Fontsource Doto](https://fontsource.org/fonts/doto/about), [Serbyte](https://www.serbyte.net/fonts/doto), [Google Fonts specimen](https://fonts.google.com/specimen/Doto/about) (page content did not render in fetch; license info from the others)
  - Silkscreen: OFL 1.1, Google Fonts repo (googlefonts/silkscreen), pixel face imported from Jason Kottke's site, originally 2001. — [googlefonts/silkscreen mirror](https://gitee.com/mirrors_googlefonts/silkscreen)
  - DotGothic16: SIL OFL 1.1, released by Fontworks, includes Japanese. — [online-fonts.com](https://online-fonts.com/fonts/dotgothic16), [Gigazine](https://www.gigazine.net/gsc_news/en/20210120-google-fonts-fontworks/)
  - Space Mono: OFL 1.1, copyright Google Inc 2016. — [Font Squirrel](https://www.fontsquirrel.com/fonts/space-mono), [online-fonts.com](https://online-fonts.com/fonts/space-mono)
  - JetBrains Mono: OFL 1.1, JetBrains Mono Project Authors. — [OFL text copy](https://www.dwellir.com/fonts/standard/OFL-JetBrainsMono.txt)
  - Other dot-style Google Fonts named by a community thread: Sixtyfour, Sixtyfour Convergence, Handjet, Workbench. — [Adobe community thread](https://community.adobe.com/t5/adobe-fonts-discussions/dot-matrix-font/td-p/15143736) (licenses not verified)
- OFL allows use, embedding and redistribution; modified versions must keep the license and copyright notices travelling with the files. — [openfontlicense.org](https://openfontlicense.org/ofl-fonts/)
- Bundling in Compose: put `.ttf` into `res/font` with lowercase names (variable fonts require API 26+ and `FontVariation`), or use `ui-text-google-fonts` with a local fallback. — [Compose fonts docs](https://developer.android.com/develop/ui/compose/text/fonts)

### Inferences
- Do not bundle Ndot, NType or Lettera: the license evidence says they are proprietary, and publishing an app using them risks takedown. The Nothing aesthetic can be matched with Doto for large numerals and headings (temperature readouts, progress percent), Space Mono (uppercase, letter-spaced) for labels, and a neutral sans (system) for body copy.
- Bundle OFL fonts in `res/font` rather than relying on the downloadable provider: works offline, avoids the months-long availability lag, supports Doto's variable axes (API 26+, with static fallback for older). Include OFL.txt files in an open-source licenses screen.
- Doto's dots are only readable at larger sizes; use it for display sizes (roughly 28 sp and above) and not for body text. This is my design judgment.
- Red accent: use a single signal red (for example around `#D71921`, which is commonly cited for Nothing, though not verified here) for the one active/alert element: print progress ring, error state, or primary toggle. Everything else in black, white and greys. Given the conflicting sources on whether Nothing uses an accent at all, treat red as a stylistic choice.
- Glyph-style UI ideas: dot-matrix progress bars (grid of dots), segmented dot indicators, 1px hairline outlines, monochrome line icons (Material Symbols outlined with weight 300 or custom), uppercase mono labels, flat surfaces with no elevation, and sharp or small radii.

### Gaps
- Nothing's official design pages and font license were not retrievable. License statements about Ndot/Lettera come from third-party copies.
- The exact Nothing red hex is unconfirmed (sources conflict).
- Google Fonts pages for Silkscreen, DotGothic16, Space Mono and JetBrains Mono were not fetched directly; licenses come from mirrors and aggregators. Whether Doto is already resolvable via the Android downloadable provider was not checked.
- The `google/fonts` repo folder names were not verified (fetch was truncated).

## 5. Architecture for two switchable visual styles in one codebase

### Takeaway
No source found specifically on this pattern, so this section is design inference built on official custom-design-system guidance. The recommended approach is a single set of screens driven by a style-token `CompositionLocal` and a small set of themed primitives, with separate composables only where layouts truly differ.

### Cited Findings
- Compose officially supports custom design systems alongside or in place of Material (link from the Material 3 page). — [Custom design systems in Compose](https://developer.android.com/develop/ui/compose/designsystems/custom)
- `MaterialExpressiveTheme`/`MaterialTheme` accept `colorScheme`, `shapes`, `typography` (and `motionScheme`), so one Material theme can already express most visual differences by data alone. — [Compose Multiplatform API reference](https://kotlinlang.org/api/compose-multiplatform/material3/androidx.compose.material3/-material-expressive-theme.html)
- MaterialKolor has adapters for other systems (Compose Unstyled, Compose Fluent), showing seed-based color can feed non-Material systems. — [MaterialKolor GitHub](https://github.com/jordond/MaterialKolor)

### Inferences
- Layer 1, shared state: ViewModels and UI state classes know nothing about style. Screens collect `ThemeSettings` from a `SettingsRepository` (DataStore).
- Layer 2, theme root: `AppTheme(settings) { }` selects a `StyleTokens` implementation (`MaterialYouTokens`, `NothingTokens`), provides it through `CompositionLocalProvider(LocalStyle provides tokens)`, and wraps content in `MaterialTheme(colorScheme, shapes, typography)` built from the tokens. Nothing then becomes a Material theme with black/white/red `ColorScheme`, tiny-radius `Shapes`, mono/dot `Typography`. All stock M3 components (Switch, Slider, TextField, Dialog) then restyle automatically with little work.
- Layer 3, tokens beyond Material: `StyleTokens` can hold extras that M3 lacks: `useHairlineBorders`, `elevationMode`, `labelCase` (uppercase), `progressStyle` (Bar vs DotMatrix), `iconWeight`, `spacingScale`, `headlineFont`. Read these in a few shared "style-aware" primitives (`AppCard`, `AppProgress`, `AppSectionHeader`, `AppTopBar`) that screens call instead of raw M3 widgets.
- Layer 4, separate composables only when needed: for signature widgets (temperature gauge, print progress hero) use `when (LocalStyle.current.kind)` to pick `MaterialProgressHero` vs `DotMatrixProgressHero` with the same parameters. Avoid duplicating whole screens, since that doubles maintenance and test cost.
- Prefer `staticCompositionLocalOf` for the style tokens since they change rarely, and wrap in `remember` so recomposition of the whole tree on change is deliberate.
- Animate a style switch with a crossfade of the root or accept an instant switch; heavy cross-style animation is unnecessary.
- Previews: add `@Preview` parameter providers for both styles to catch regressions on each screen.

### Gaps
- No public sample app implementing two switchable design systems was found, so the pattern is unverified in practice.
