# Android platform: LAN discovery, print notifications, widgets, secure storage, G-code thumbnails (Android 15/16, as of Nov 2026)

## 1. Network auto-discovery (NsdManager, multicast, SSDP, UDP, subnet scan, permissions)

### Takeaway
For minSdk 26 / targetSdk 35 no special runtime permission gates LAN access: INTERNET (plus normal permissions ACCESS_WIFI_STATE / CHANGE_WIFI_MULTICAST_STATE) is enough. The big upcoming change is Android 17 (target SDK 37): the ACCESS_LOCAL_NETWORK runtime permission (NEARBY_DEVICES group) blocks ALL LAN traffic (TCP, UDP, mDNS, .local resolution) by default. NEARBY_WIFI_DEVICES was only a temporary Android 16 opt-in mechanism and is now deprecated for this purpose. So do NOT request NEARBY_WIFI_DEVICES or ACCESS_LOCAL_NETWORK while targeting 36 or lower.

### Cited Findings
- Android 17 (API 37) enforces local network protection for apps targeting 37+; the Android 16 opt-in (RESTRICT_LOCAL_NETWORK compat flag + NEARBY_WIFI_DEVICES) is deprecated; on Android 16 "local network access is open", on Android 17 it is "blocked by default" and needs ACCESS_LOCAL_NETWORK — [Local network permission](https://developer.android.com/privacy-and-security/local-network-permission)
- Apps targeting SDK 36 or lower get implicit access via INTERNET; docs say not to add ACCESS_LOCAL_NETWORK to the manifest or request it at runtime for those targets — [same](https://developer.android.com/privacy-and-security/local-network-permission)
- Once enforced, the permission is required for outgoing/incoming TCP and for sending/receiving UDP unicast/multicast/broadcast; it applies to all networking APIs (OkHttp, Cronet, sockets, NsdManager); resolving `.local` names also requires it; failures show as TCP timeouts and UDP EPERM — [same](https://developer.android.com/privacy-and-security/local-network-permission)
- ACCESS_LOCAL_NETWORK is in the NEARBY_DEVICES permission group; users who already granted another permission in the group (e.g. Bluetooth) are not re-prompted; a counter-reset strategy allows re-requesting after prior denials — [same](https://developer.android.com/privacy-and-security/local-network-permission)
- Android 17 alternative for mDNS: NsdManager system picker (`DiscoveryRequest.Builder("_http._tcp").setFlags(FLAG_SHOW_PICKER)` + `registerServiceInfoCallback`); IPs obtained that way need no ACCESS_LOCAL_NETWORK. The picker yields one user-selected device, so auto-scan / UDP / SSDP designs need the runtime permission — [same](https://developer.android.com/privacy-and-security/local-network-permission)
- Official NSD guide: set up a DiscoveryListener, call `discoverServices(type, PROTOCOL_DNS_SD, listener)`, then `resolveService` for host/port; discovery is expensive and should be stopped on pause and restarted on resume — [Use NSD](https://developer.android.com/develop/connectivity/wifi/use-nsd)
- Multicast lock (snippet-level, from a search summary of the NsdManager reference): before T extensions 7 apps need a MulticastLock to receive mDNS; from T ext. 7 the system manages multicast for foreground apps and background apps should avoid the lock — [NsdManager reference](https://developer.android.com/reference/android/net/nsd/NsdManager.html)
- Bambu Lab printers broadcast SSDP-style NOTIFY packets with NT `urn:bambulab-com:device:3dprinter:1` from port 1900 to ports 1990 and 2021 about every 5 s, with fields DevModel, DevName, DevConnect ("lan"), DevBind; MQTT over TLS on 8883 (user `bblp`, access code as password); LAN video on 6000/tcp. Unofficial community sources only — [Alex-Schaefer gist](https://gist.github.com/Alex-Schaefer/72a9e2491a42da2ef99fb87601955cc3), [Bambu forum](https://forum.bambulab.com/t/use-ssdp-standards/7173), [nuxx.net](https://nuxx.net/blog/?p=19855)
- FlashForge Adventurer 4 Pro/Lite: TCP port 8899, no authentication, text G/M-code interface (33 commands in community docs); a separate UDP auto-discovery protocol exists — [Parallel-7/flashforge-api-docs](https://github.com/Parallel-7/flashforge-api-docs)

### Inferences
- Target SDK 35 needs no extra permission for LAN scanning; plan a migration step for target 37: declare ACCESS_LOCAL_NETWORK, request it with a rationale screen, handle denial gracefully. For this LAN-only app the permission is core, and the picker alone is insufficient for subnet scan / UDP / Bambu SSDP / FlashForge UDP.
- Suggested discovery order: (1) NsdManager for `_http._tcp` (Moonraker/Mainsail; then probe `/server/info`) and `_octoprint._tcp`; (2) UDP listener on 1990/2021 for Bambu and FlashForge UDP broadcast; (3) fallback coroutine sweep of the /24 derived from LinkProperties, probing 8899, 7125, 80, 5000 with roughly 300-800 ms connect timeouts and a semaphore of ~64-128, then protocol-specific confirmation. Timeouts and concurrency are my engineering suggestions, not sourced.
- Bind sockets to the Wi-Fi Network so traffic does not go over mobile data (common practice, not sourced).

### Gaps
- FlashForge UDP discovery port and packet format: wiki page not retrievable (48899 is often cited but unverified here).
- M115/M105/M27 behavior on Adventurer 4 not confirmed.
- OctoPrint `_octoprint._tcp` and Moonraker zeroconf advertisement specifics not researched.
- No authoritative Android 14/15 mDNS pitfalls found beyond the multicast-lock note.
- Conflict: a pub.dev plugin page claims an Android 16 implicit local-network permission tied to nearby Wi-Fi; official docs say enforcement is Android 17 (Android 16 was opt-in only). Official docs preferred.

## 2. Background monitoring and print-completion notifications without FCM

### Takeaway
There is no officially sanctioned always-on option: dataSync FGS is capped at 6 h per 24 h on target 15+, specialUse needs Play review but has no documented timeout, and WorkManager periodic work is limited to 15 min minimum. Best design is a hybrid: a user-started monitoring foreground service while a print is active, plus a WorkManager periodic fallback, plus battery-optimization onboarding for Samsung/Xiaomi.

### Cited Findings
- Android 15 (target 15+): `dataSync` and `mediaProcessing` FGS get 6 hours total per 24 h; then `Service.onTimeout(int,int)` is called and the service must call `stopSelf()` within seconds or the app crashes with RemoteServiceException; the timer resets when the user brings the app to the foreground; starting another after exhaustion throws ForegroundServiceStartNotAllowedException — [FGS timeouts](https://developer.android.com/develop/background-work/services/fgs/timeout)
- Test with `adb shell am compat enable FGS_INTRODUCE_TIME_LIMITS <pkg>` and shorten with `device_config put activity_manager data_sync_fgs_timeout_duration <ms>` — [same](https://developer.android.com/develop/background-work/services/fgs/timeout)
- Since Android 14 every FGS must declare a type plus the matching FOREGROUND_SERVICE_* permission; Play Console requires FGS type declaration — [FGS types](https://developer.android.com/develop/background-work/services/fgs/service-types)
- `specialUse`: FOREGROUND_SERVICE_SPECIAL_USE, no runtime prerequisite, requires `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="...">` free-form explanation reviewed in Play Console. The docs list timeouts only for dataSync/mediaProcessing/shortService — [FGS types](https://developer.android.com/develop/background-work/services/fgs/service-types), [timeouts](https://developer.android.com/develop/background-work/services/fgs/timeout)
- `connectedDevice`: allowed if the manifest declares at least one of CHANGE_NETWORK_STATE, CHANGE_WIFI_STATE, CHANGE_WIFI_MULTICAST_STATE, NFC, TRANSMIT_IR (or Bluetooth/UWB runtime permissions); described as interaction with external devices over Bluetooth, NFC, IR, USB or network — [FGS types](https://developer.android.com/develop/background-work/services/fgs/service-types)
- dataSync cannot be launched from a BOOT_COMPLETED receiver on target 15+ — [FGS types](https://developer.android.com/develop/background-work/services/fgs/service-types)
- `shortService` is about 3 min; `systemExempted` is limited to device owners, VPN apps, apps holding exact-alarm permissions, etc. — [FGS types](https://developer.android.com/develop/background-work/services/fgs/service-types)
- WorkManager: minimum periodic interval 15 min (same as JobScheduler); actual run time depends on constraints and system optimizations; flex interval supported; unmet constraints can delay or skip runs — [Define work](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work)
- Expedited work suits short, user-important, start-now tasks; quota depends on standby bucket and process importance; less affected by Doze/Battery Saver; no quota limit while app is foreground; `setExpedited(OutOfQuotaPolicy...)`; CoroutineWorker must implement getForegroundInfo — [Define work](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work)
- OEM kills: dontkillmyapp lists Samsung (severe default restriction since Android 11; mitigation: set app to "Don't optimize" in battery settings) and Xiaomi (autostart setting plus cleaner apps kill services when app is swiped from recents); forum reports say even FGS + wakelock may not survive on Xiaomi without user whitelisting. Sources are forum/blog quality — [dontkillmyapp Samsung](https://dontkillmyapp.com/samsung), [aggregation](https://androidauthority.com/dontkillmyapp-background-apps-943151)
- Android 16 Live Updates (promoted ongoing notifications) require: `POST_PROMOTED_NOTIFICATIONS` (non-runtime permission), `setRequestPromotedOngoing(true)`, ongoing, contentTitle, no RemoteViews, not colorized, not a group summary, channel not IMPORTANCE_MIN; style Standard/BigText/Call/Progress/Metric. Check `NotificationManager.canPostPromotedNotifications()`; deep link via `Settings.ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS`; status-chip text via `setShortCriticalText` / `setWhen` — [Live updates](https://developer.android.com/develop/ui/views/notifications/live-update)
- Live Update policy: must be ongoing, user-initiated, time-sensitive; alerts/ads/chat are inappropriate; if the user asked to monitor a background event it is allowed but should include an "Unpin" action; don't repost dismissed ones (use setDeleteIntent); OEMs may add criteria — [Live updates](https://developer.android.com/develop/ui/views/notifications/live-update)
- `Notification.ProgressStyle` (Android 16) supports segments, points, tracker icon, `setProgress`; sample in android/platform-samples (live-updates) — [Progress-centric notifications](https://developer.android.com/about/versions/16/features/progress-centric-notifications)

### Inferences
- While a print runs and the user has enabled "Monitor", run a `specialUse` (or arguably `connectedDevice`) FGS and implement onTimeout anyway. Avoid dataSync as primary: long prints exceed 6 h. Play justification text for specialUse: "monitor local-network 3D printer and alert on completion/failure".
- Inside the FGS be event-driven where the protocol allows (Moonraker WebSocket, OctoPrint/Bambu MQTT) and poll FlashForge TCP every ~5-10 s. Use separate channels: low-importance ongoing progress; high-importance alerts for completed / failed / filament runout / target temperature reached. De-duplicate on state transitions persisted in DataStore.
- Fallback: WorkManager periodic (15 min) polling, re-armed on boot/network change; accept 15+ min latency when FGS is killed. Optionally schedule a one-shot work at predicted end time (exact-alarm policy not researched).
- Use ProgressStyle + promoted ongoing on Android 16+ only for an actively monitored print; use a standard progress notification on older versions (androidx compat availability not verified).
- POST_NOTIFICATIONS is a runtime permission on Android 13+ (well known; not separately sourced here); ask in context when the user enables monitoring.
- Onboarding should deep-link users to battery-optimization settings and OEM autostart pages (Samsung/Xiaomi).

### Gaps
- No primary source confirming specialUse has no timeout on Android 16, nor Play approval likelihood for a printer monitor.
- No official statement fetched on Doze effects for FGS network access.
- AlarmManager exact-alarm policy and POST_NOTIFICATIONS docs not fetched.
- Android 16/17 FGS or job-quota changes not specifically researched.

## 3. Glance widget, Quick Settings tile, App Shortcuts

### Takeaway
Glance widgets update via `update/updateAll` triggered from WorkManager or the monitoring service. QS tiles should be small interactive controls (pause/cancel), not read-only info displays.

### Cited Findings
- GlanceAppWidget: call `update(context, glanceId)` or `updateAll(context)`; `updatePeriodMillis` allows at most one update per 30 min; for more frequent refresh use WorkManager (15 min periodic) calling `updateAll` from doWork — [Manage and update GlanceAppWidget](https://developer.android.com/jetpack/compose/glance/glance-app-widget)
- Glance 1.1.0 is used as a stable dependency in tutorials — [Zenn](https://zenn.dev/giglancer/articles/56cee019669de4) (progress bar specifics not verified)
- QS tile: `TileService` with BIND_QUICK_SETTINGS_TILE; label max 18 chars; 24dp white vector icon; recommended active mode (`ACTIVE_TILE` meta-data, `requestListeningState`), where the tile can be updated once before onStopListening; states ACTIVE/INACTIVE/UNAVAILABLE; `requestAddTileService()` (Android 13+) prompts the user to add the tile; docs recommend max two tiles per app and say to avoid tiles that only display information (use widget/notification) or only launch the app (use shortcut); `startActivityAndCollapse` needs FLAG_ACTIVITY_NEW_TASK on API 28+; `unlockAndRun` for unsafe actions on locked devices — [Quick Settings tiles](https://developer.android.com/develop/ui/views/quicksettings-tiles)
- Android docs say quick access to app functions should be an app widget or custom QS tile rather than a Live Update — [Live updates](https://developer.android.com/develop/ui/views/notifications/live-update)

### Inferences
- Widget: persist last known printer state (DataStore) from the monitor service/worker and call `updateAll`; show progress, ETA, temperatures and a "last updated" stamp since updates are best-effort. Tile: pause/resume or stop with confirmation via `showDialog`.
- Static/dynamic App Shortcuts (e.g. "Open printer X", "Camera") are the docs-recommended way to launch the app quickly; not sourced further in this pass.

### Gaps
- Glance progress bar API support and RemoteViews size limits not verified.
- App Shortcuts documentation not fetched.

## 4. Secure credential storage and settings

### Takeaway
Jetpack Security Crypto (EncryptedSharedPreferences) is deprecated or effectively end-of-life. The common replacement is DataStore for persistence plus Tink AEAD (AES-256-GCM) with the keyset wrapped by an Android Keystore master key; `androidx.datastore:datastore-tink` offers an AeadSerializer. No single official Google doc combines these.

### Cited Findings
- All Jetpack Security Crypto APIs deprecated in 1.1.0 with no further releases planned; reasons are the author's inference (Keystore inconsistency across OEMs, push to DataStore, direct Tink use) — [Include Security blog, Aug 2026](https://blog.includesecurity.com/2026/08/encryptedsharedpreferences-is-dead-heres-what-you-should-use-instead/)
- Recommended: DataStore + Tink (`AeadConfig.register()`, `AndroidKeysetManager`, `AES256_GCM`, `android-keystore://` master key) or `datastore-tink` `AeadSerializer` with associated data; set `allowBackup=false`; prefer not to persist secrets at all if avoidable; avoid String for secrets — [same](https://blog.includesecurity.com/2026/08/encryptedsharedpreferences-is-dead-heres-what-you-should-use-instead/)
- Other 2025-2026 guides: [ProAndroidDev](https://proandroiddev.com/goodbye-encryptedsharedpreferences-a-2026-migration-guide-4b819b4a537a), [KINTO Tech](https://blog.kinto-technologies.com/posts/2025-06-16-encrypted-shared-preferences-migration) (chose Tink; its benchmark showed Tink far faster than direct Keystore operations). Sources disagree on whether Google formally flags it as deprecated (an academic paper in the results says not yet flagged). `SharedPreferencesMigration` can move existing data to DataStore (per guide summaries).

### Inferences
- Encrypt per-printer secrets (Bambu access code, OctoPrint API key, Moonraker token) with Tink AEAD using printer ID as associated data; keep non-secret settings (IP, name, poll interval, notification prefs) in plain DataStore. FlashForge Adventurer 4 has no auth, so little to protect there.

### Gaps
- No official developer.android.com page found on this deprecation; sample code not compiled or tested.

## 5. G-code thumbnails and layer preview

### Takeaway
Thumbnail extraction is straightforward: slicer comment blocks with base64 PNG (or QOI) can be found by line scanning; Moonraker already exposes thumbnails in file metadata. A simple 2D per-layer preview is feasible with streaming parsing and Compose Canvas; full 3D is heavier.

### Cited Findings
- PrusaSlicer embeds thumbnails as `; thumbnail begin <w>x<h> <bytes>` followed by base64 lines prefixed `; ` and a `; thumbnail end` marker; multiple sizes and PNG or QOI formats; Mainsail recommends 32x32/PNG and 400x300/PNG — [Mainsail PrusaSlicer docs](https://docs.mainsail.xyz/slicers/prusaslicer/), [Prusa forum](https://forum.prusa3d.com/forum/hardware-firmware-and-software-help/how-to-view-gcode-thumbnails-on-pc/)
- Moonraker file metadata includes thumbnails (width/height/size/relative_path) and slicer info; unparsable fields are omitted — [Moonraker file manager API](https://moonraker.readthedocs.io/en/latest/external_api/file_manager/)
- Binary G-code (.bgcode) is not plain text and needs a separate reader — [PrintStash release](https://github.com/xiao-villamor/PrintStash/releases/tag/v0.7.3)

### Inferences
- Read only the first few hundred KB of a G-code file (HTTP range or partial download) to find the thumbnail block; decode PNG with BitmapFactory; QOI needs a small decoder. Header-comment thumbnails mean no full download is needed for Moonraker/OctoPrint; on Moonraker just fetch the thumbnail URL.
- Layer preview: parse G0/G1 X/Y/E, group by `;LAYER_CHANGE` or Z change, draw the selected layer on Canvas, downsample segments, parse on Dispatchers.Default, cap file size for memory.

### Gaps
- No source on FlashForge .gx thumbnail format or Bambu 3MF thumbnail extraction; no existing Android G-code viewer library evaluated; performance not measured.
