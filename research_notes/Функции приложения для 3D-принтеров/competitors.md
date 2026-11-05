# Competitor feature sets of 3D-printer remote-control apps (Android focus, 2026)

Method note: about 12 web searches/fetches. Direct access to Google Play and Reddit was not available, so review/complaint evidence is indirect (tracker sites, forum threads, vendor blogs). Vendor blogs (Obico, SimplyPrint) are self-interested.

## Which features are table stakes vs differentiators?

### Takeaway
Table stakes across the apps checked are print status/progress, pause/resume/cancel, temperatures, a live webcam, file upload/start, and push notifications. Differentiators are AI failure detection (Obico), cloud slicing (Creality), deep Klipper tooling (Fluidd/Mainsail: bed mesh, macros, console, Spoolman), multi-printer and watch/widget support (OctoApp, OctoPod), and AMS/filament management (Bambu).

### Cited Findings
- Fluidd feature list: G-code viewer with thumbnails, object exclusion and bed mesh visualization; job queue; temperature charts with presets; multi-camera (MJPEG, HLS, WebRTC); console with history/autocomplete/output filters; macros with categories and visibility toggles; Spoolman filament tracking with QR scan; file manager and editor; multi-printer from one instance; timelapse via Moonraker; JWT/LDAP auth; Obico/OctoEverywhere integrations — [Fluidd docs](https://docs.fluidd.xyz/features/)
- Mainsail is described (by competitor SimplyPrint, so biased) as a local, fast UI with config editing, G-code preview, bed-mesh visualisation, macros and theming — [SimplyPrint](https://simplyprint.io/alternatives/mainsail.md). Obico's comparison says Mainsail offers more customization than Fluidd, and that Fluidd lacks mobile-specific customization — [Obico](https://www.obico.io/fr-FR/blog/mainsail-vs-fluidd-vs-octoprint/)
- OctoApp (Android): print control, full file management, terminal, G-code viewer, multi-printer, multi-camera webcam, webcam snapshots in notifications, filament notifications, floating webcam bubble, Wear OS app (no home-screen widget found in results) — [APKMirror listing](https://www.apkmirror.com/apk/livingroom-workbench/octoapp-octoprint-klipper/octoapp-octoprint-klipper-2-2-38-release/octoapp-octoprint-klipper-2-2-38-android-apk-download/); Obico's 2022-era article: 4.9 stars, 50k+ downloads, 12 MB, simple guided Connect/Prepare/Print, optional companion plugin for push notifications with end-to-end encryption, LAN-only unless paired with Obico — [Obico blog](https://www.obico.io/blog/octoprint-mobile-apps-access-octoprint-on-the-go/)
- Other OctoPrint clients: OctoRemote (multi-printer, file up/download, temp/movement control, timelapse access, setup via IP + API key); Printoid (multi-server, files, live cam, tablets/Chromebook); OctoPod on iOS (dashboard of all printers, Apple Watch/Siri, widgets, auto-discovery, free/open-source) — [Obico blog](https://www.obico.io/blog/octoprint-mobile-apps-access-octoprint-on-the-go/)
- Obico: AI failure detection, webcam streaming, printer control, access to OctoPrint/Mainsail/Fluidd, live stream sharing, remote access without VPN/port forwarding, AGPLv3 open source — [Obico blog](https://www.obico.io/blog/octoprint-mobile-apps-access-octoprint-on-the-go/). Free cloud plan: AI detection 10 h/month, 1 printer; Premium $6.99/mo billed annually; self-hosting free but needs own server, keeps images local — [Obico pricing guide](https://www.obico.io/blog/is-obico-free-pricing-guide/) (search snippets via [Obico](https://www.obico.io/ru-RU/blog/is-obico-free-pricing-guide/))
- Bambu Handy: live view, automatic timelapse, error alerts with troubleshooting, AMS humidity display and filament management, print history with filters and reprint, notification quiet mode — [App Store listing](https://apps.apple.com/us/app/bambu-handy/id1625671285), [APKMirror release notes](https://www.apkmirror.com/apk/shanghai-lunkuo-technology-co-ltd/bambu-handy/bambu-handy-3-1-0-release)
- Prusa app: real-time overview of all printers in Prusa Connect, start/pause/stop, push notifications, Printables integration, add printers. Prusa Connect is cloud (free, 1 GB storage); PrusaLink is local-only and limited — [Prusa help](https://help.prusa3d.com/article/prusalink-and-prusaconnect_302608), [App Store](https://apps.apple.com/gr/app/prusa/id6477531937)
- Creality Cloud: cloud slicing, remote control, Wi-Fi Box integration, model library — [All3DP](https://all3dp.com/2/creality-cloud-app/?critical=1)
- Flash Maker (FlashForge official): remote monitoring, group control of multiple printers, quick reprint from history — [AppGoblin](https://appgoblin.info/apps/com.flashforge.flashmaker)

### Inferences
- Rough matrix (Y = present per sources above; ? = not confirmed in my sources):

| Feature | Fluidd/Mainsail | OctoApp | Obico | Bambu Handy | Prusa app | Creality Cloud | Flash Maker |
|---|---|---|---|---|---|---|---|
| Status/progress, pause/cancel | Y | Y | Y | Y | Y | Y | Y |
| Temp control/graphs | Y (graphs) | Y | Y | ? | ? | ? | ? |
| Live webcam | Y (multi) | Y (multi) | Y | Y | ? | Y | ? |
| Push notifications | via plugins | Y (plugin) | Y | Y (flaky) | Y | ? | ? |
| G-code preview | Y | Y | Y (via web UI) | thumbnails | ? | cloud slicer | ? |
| AI failure detection | via Obico | via Obico | Y | Y (spaghetti, cloud) | ? | ? | ? |
| Timelapse | Y | access | Y | Y auto | ? | ? | ? |
| Multi-printer | Y | Y | paid | Y | Y | ? | Y (group) |
| Macros/console | Y | terminal | via UI | N | N | N | N |
| Bed mesh | Y | ? | via UI | N | N | N | N |
| Filament tracking | Spoolman | ? | ? | AMS | ? | ? | ? |
| Print history/stats | Y | ? | Y | Y | Y | ? | Y |
| Wear OS/widgets | N | Wear OS | ? | ? | ? | ? | ? |
- Prioritized by apparent value: (1) reliable connection/discovery, (2) live camera, (3) accurate progress/ETA plus temps, (4) reliable completion/error notifications, (5) file upload from phone and start, (6) thumbnails/G-code preview, (7) multi-printer, (8) timelapse/snapshots, (9) history/stats, (10) widgets/watch, (11) AI detection (needs compute/cloud).

### Gaps
- No exact Mainsail-vs-Fluidd differences confirmed; no confirmation of OctoApp widgets, bed mesh, history; no Creality/Prusa feature lists beyond store summaries.

## What do users praise and complain about?

### Takeaway
Praise goes to simple, local, reliable UIs (OctoApp 4.9 stars, open-source tools). Complaints concentrate on connectivity/cloud dependence, LAN-mode limits, unreliable notifications, ads, and weak FlashForge app quality (about 1.8-2.0 stars).

### Cited Findings
- Flash Maker: roughly 1.8-1.94 stars on Google Play (107-377 ratings depending on tracker; about 74K installs) and about 2.0 on iOS (511 reviews); one review asks for STL sending and complains of ads; official guide says printer connection is not supported in LAN mode — [AppGoblin](https://appgoblin.info/apps/com.flashforge.flashmaker), [FlashForge guide](https://www.flashforge.com/a/docs/flash-maker/flash-maker-quick-start-guide). Trackers disagree on counts.
- Adventurer 4 reviews: "Solid hardware, buggy software"; built-in camera needs desktop FlashPrint — [Tom's Hardware](https://tomshardware.com/reviews/flashforge-adventurer-4-3d-printer-review), [Tom's Guide](https://www.tomsguide.com/reviews/flashforge-adventurer-4)
- Bambu: Handy not available in LAN-only mode; users complain about bind failures, no live view/print monitoring in LAN mode, Studio contacting cloud servers, later cloud outages; a third-party LAN monitoring app was built because of this — [Bambu forum](https://forum.bambulab.com/t/lan-only-and-bambu-mobile/24697), [forum](https://forum.bambulab.com/t/please-make-a-fully-functional-lan-only-mode/122733), [Reddit LANbu](https://reddit.birdcat.cafe/r/BambuLab/comments/1kioov0/lanbu_lan_only_mode_print_monitoring_app_for). Reviews also mention late/missing notifications, intermittent camera errors, AMS slot selection problems — [App Store](https://apps.apple.com/us/app/bambu-handy/id1625671285)
- Creality Cloud: connectivity problems, camera stopped after update, ads and gamification, limited slicing settings, crashes, poor support; ratings conflict by source (3.94 to 4.86); Creality Cloud Lite is 3.2 — [JustUseApp](https://justuseapp.com/en/app/1577663728/creality-cloud-3d-printing/reviews), [Android Stats](https://android-stats.com/d/com.cxsw.sdprinter), [SplitMetrics](https://splitmetrics.com/apps/creality-cloud-3d-printing/id1577663728)
- Prusa: App Store reviews mixed, one calls it "very incomplete"; forum thread says PrusaLink needs attention — [Prusa forum](https://forum.prusa3d.com/forum/introcuctions-instrutctions/prusa-link-is-in-desperate-need-of-attention-embarrassing-basic-with-oversights)
- OctoApp cons: LAN-only without Obico, last update (as of that 2022 article) stale — [Obico blog](https://www.obico.io/blog/octoprint-mobile-apps-access-octoprint-on-the-go/)

### Inferences
- A free, ad-free, LAN-only, stable app with a working camera and good discovery directly addresses the top complaints about Flash Maker.

### Gaps
- No direct Google Play review text for OctoApp, Obico, Handy, Prusa; no Reddit r/FlashForge or r/klippers threads retrieved.

## Which features are feasible for a free offline LAN-only Android app (FlashForge Adventurer 4)?

### Takeaway
Core monitoring and control are feasible via the reverse-engineered TCP 8899 M-code protocol plus UDP discovery; the camera is probably an MJPEG stream on port 8080 (unconfirmed for A4). Cloud-dependent features (AI detection, cloud slicing, remote access outside LAN) are not.

### Cited Findings
- Adventurer 4 uses TCP port 8899, no authentication, text G/M-codes; UDP discovery on 48899 — [Parallel-7 flashforge-api-docs](https://awesome.ecosyste.ms/projects/github.com%2Fparallel-7%2Fflashforge-api-docs). Unofficial; SimplyPrint says FlashForge documents no local API — [SimplyPrint](https://simplyprint.io/compatibility/flashforge-adventurer-4-lite.md)
- Supported on A4: M104/M140 set temps, M105 temps, M27 progress, M661 file list (data after ok), M23/M24/M25/M26 select/start/pause/cancel, M146 LED RGB, M106/M107 fan on/off only, M112 e-stop, M114/M119 position/status, M115 info, M650 calibration, M662 thumbnail, M663 positions, M610 rename; no camera commands listed — [M Code Reference](https://github.com/Parallel-7/flashforge-api-docs/wiki/M-Code-Reference)
- Camera: likely `http://<ip>:8080/?action=stream` (MJPEG) on sibling models; not confirmed for A4 — [iSpyConnect](https://www.ispyconnect.com/camera/flashforge), [OctoPrint forum](https://community.octoprint.org/t/how-to-set-flashforge-adventurer-builtin-camera-as-octoprint-camera/40541)
- Python client ffpp tested on Adventurer 4 — [PyPI](https://pypi.org/project/ffpp)
- Obico self-hosting keeps images local but needs a server (GPU helpful) — [Obico](https://www.obico.io/blog/is-obico-free-pricing-guide/)

### Inferences (LAN feasibility rating)
- High: discovery, status/progress/ETA, temps (read and set), pause/resume/cancel, LED, file list, start stored file, thumbnails (M662), multi-printer list, local notifications via foreground service polling, local print history log kept in app, widgets, Wear/quick tile, dark UI.
- Medium: live camera MJPEG (verify port 8080), snapshots and local timelapse by saving frames on phone, file upload from phone (needs protocol, e.g. M28/M29 style; not confirmed), G-code preview/3D viewer parsed on device (heavy but doable), temperature graphs built from polling, fan control only on/off.
- Low: AI failure detection (could be on-device TFLite with low accuracy; no ready model), remote access outside LAN (needs VPN/relay), cloud slicing, bed mesh/macros/console (Klipper-only; A4 has no Klipper, though a raw M-code console is trivial), Spoolman-style tracking (could be local-only manual).
- CIS note: LAN-only avoids FlashForge/Bambu/Creality/Prusa cloud entirely; this is a major advantage versus Flash Maker, which needs internet and an account per the cited guide.

### Gaps
- Upload protocol and A4 camera endpoint unverified; no checks of the Adventurer 4 firmware version differences; Apple/Google reviews not read first-hand.
