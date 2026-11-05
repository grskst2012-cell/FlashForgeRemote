# Bambu Lab LAN-mode control (A1, A1 mini, P1P, P1S, X1C, H2D) for an Android Kotlin app

## 1. MQTT over TLS (port 8883): topics, commands, status fields

### Takeaway
The printer runs a local MQTT broker on 8883 (TLS), user `bblp`, password = LAN access code. Topics are `device/{serial}/report` (subscribe) and `device/{serial}/request` (publish). Commands are JSON wrapped in a type key, with a `sequence_id`.

### Cited Findings
- Local broker is `mqtt://{PRINTER_IP}:8883` with TLS required, username `bblp`, password = LAN access code. A `#` wildcard subscription is supported. All messages are JSON. — [OpenBambuAPI mqtt.md](https://github.com/Doridian/OpenBambuAPI/blob/main/mqtt.md)
- Topics: `device/{DEVICE_ID}/report` (printer to client) and `device/{DEVICE_ID}/request` (client to printer). — [OpenBambuAPI mqtt.md](https://github.com/Doridian/OpenBambuAPI/blob/main/mqtt.md)
- Envelope: `{"print": {"sequence_id": "0", "command": "<CMD>", ...}}`. Other type keys are `system`, `camera`, `xcam`, `pushing`, `upgrade`, `info`, `mc_print`. Reports echo `sequence_id` and carry `result` and an optional `reason`. — [OpenBambuAPI mqtt.md](https://github.com/Doridian/OpenBambuAPI/blob/main/mqtt.md)
- pushall: `{"pushing":{"sequence_id":"0","command":"pushall","version":1,"push_target":1}}`. It is required on the P1 series because P1 only sends changed values in `push_status`; X1 sends the full object each time. The doc advises not calling it more often than every 5 minutes on the P1P. — [OpenBambuAPI mqtt.md](https://github.com/Doridian/OpenBambuAPI/blob/main/mqtt.md)
- pause, resume, stop: `{"print":{"sequence_id":"0","command":"pause","param":""}}`. Use `resume` or `stop` in place of `pause`. The doc suggests QoS 1. — same source
- gcode_line: `{"print":{"sequence_id":"0","command":"gcode_line","param":"M420","user_id":"1234"}}`. `param` can hold multiple lines separated by `\n`. — same source
- print_speed: `{"print":{"sequence_id":"0","command":"print_speed","param":"2"}}`. The value is a string: 1 silent, 2 standard, 3 sport, 4 ludicrous. — same source
- ledctrl: `{"system":{"sequence_id":"0","command":"ledctrl","led_node":"chamber_light","led_mode":"on","led_on_time":500,"led_off_time":500,"loop_times":1,"interval_time":1000}}`. `led_node` is `chamber_light` or `work_light`. `led_mode` is on, off or flashing. The timing fields must be present even when not flashing. — same source
- project_file (start a print): the print object includes `command:"project_file"`, `param:"Metadata/plate_X.gcode"` (plate path inside the 3mf), `url:"file:///mnt/sdcard"` (the doc's own example; the exact path for the filename is not shown there, see Gaps), `file`, `project_id/profile_id/task_id/subtask_id` as `"0"` for local prints, `bed_type:"auto"`, booleans `timelapse, bed_levelling, flow_cali, vibration_cali, layer_inspect, use_ams`, and `ams_mapping`. — same source
- ams_mapping: a 5-element array with -1 for unused slots, filled from the right. One colour is `[-1,-1,-1,-1,X]`. Set `use_ams:true`. A wrong mapping makes the printer pause before starting. (This is the OpenBambuAPI description; newer firmware and slicers may use a different layout, see Gaps.) — same source
- Status fields in `print` push_status: `gcode_state` (e.g. "IDLE"; the doc lists no other values), `mc_percent`, `mc_remaining_time`, `mc_print_stage`, `mc_print_error_code`, `layer_num`, `total_layer_num`, `nozzle_temper`, `nozzle_target_temper`, `bed_temper`, `bed_target_temper`, `chamber_temper`, `cooling_fan_speed`, `big_fan1_speed`, `big_fan2_speed`, `heatbreak_fan_speed`, `spd_lvl`, `gcode_file`, `subtask_name`, `wifi_signal`, `lights_report` (array of node and mode), `ipcam`, `xcam`, `upgrade_state`, `upload`. — same source
- AMS: `print.ams.ams[]` units have `id`, `humidity`, `temp`, `tray[]`. Each tray has `tray_type`, `tray_color` (RRGGBBAA), `remain`, `nozzle_temp_min/max`, `tag_uid`, and more. `tray_now` uses 255 for none and 254 for the external spool; otherwise `ams_id*4+tray_id`. `vt_tray` is the external spool. — same source
- HMS: the doc shows only an empty `"hms": []` and does not document the entry structure. — same source

### Inferences
- A minimal Android client needs: connect, subscribe to `/report`, publish pushall on connect, parse `print` objects, and merge partial updates into a state object (required for P1/A1).
- From my background knowledge (unverified here, mark uncertain): `gcode_state` values are commonly IDLE, PREPARE, RUNNING, PAUSE, FINISH, FAILED. HMS entries are commonly `{attr, code}` integers. `mc_remaining_time` is in minutes.

### Gaps
- Complete `gcode_state` enum, HMS entry format and code-to-text mapping were not confirmed from a fetched source.
- H2D-specific MQTT differences (dual nozzle, extruder fields, two-AMS layout) were not found.
- Exact `project_file` URL format, and whether new firmware needs `ftp://` or `file:///sdcard/x.3mf`, was not verified. Check ha-bambulab or bambulabs_api source.

## 2. Camera: RTSPS 322 vs proprietary port 6000

### Takeaway
X1 and the newer H-series use RTSPS on port 322 and need a video player stack. A1 and P1 use a simple TLS socket on port 6000 that streams JPEG frames. That is easy to decode on Android with plain `BitmapFactory`, no ffmpeg needed.

### Cited Findings
- RTSPS: `rtsps://{IP}:322/streaming/live/1`, TLS, user `bblp`, password = access code. Applies to X1, P2S, H2S, H2D. On H2S and H2D the server is off by default. Enable it on the touchscreen (Settings, General, "LAN Mode Liveview"; label varies by firmware). Then `push_status` reports `ipcam.rtsp_url`. — [OpenBambuAPI video.md](https://github.com/Doridian/OpenBambuAPI/blob/main/video.md)
- Port 6000 (A1, P1): TLS socket, little-endian. The auth packet is 80 bytes: u32 payload size 0x40, u32 type 0x3000, u32 flags 0, u32 0, then username (32 bytes, null-padded) and password (32 bytes, null-padded). — same source
- Each frame has a 16-byte header: u32 JPEG length, u32 itrack 0, u32 flags 1, u32 0. Then comes the JPEG, which can arrive in chunks (for example 4096 bytes), so read exactly N bytes. A valid JPEG starts with FF D8 and ends with FF D9. — same source

### Inferences
- Port 6000 is easy on Android: `SSLSocket` with a trust-all or pinned-CA TrustManager, read frames in a loop, decode with `BitmapFactory.decodeByteArray`.
- RTSPS 322 on Android can use Media3/ExoPlayer's RTSP module, which supports `rtsps://` in recent versions. I did not verify this, and the printer's non-public CA needs a custom SSLSocketFactory. LibVLC is an alternative but adds a large binary. Mark UNCERTAIN.

### Gaps
- Whether Media3 RTSP works with the Bambu RTSPS stream was not tested or found.
- Which A1/P1 firmware versions expose port 6000 under Authorization Control was not confirmed.

## 3. File upload via FTPS and starting a print

### Takeaway
FTPS is implicit TLS on port 990, user `bblp`, password = access code. Upload the .3mf, then send `project_file` over MQTT.

### Cited Findings
- `ftps://{IP}:990`, implicit TLS, user `bblp`, password = access code (`dev_access_code`). — [OpenBambuAPI ftp.md](https://github.com/Doridian/OpenBambuAPI/blob/main/ftp.md). The page gives no directory or quirk details.
- TLS certificate: the printer's certificate is issued by a Bambu Lab CA (not a public CA). Clients must trust the BBL CA (`ca_cert.pem` in OpenBambuAPI's examples). The CN is the printer serial, so use SNI or check the CN manually when connecting by IP. The doc advises against disabling all validation. — [OpenBambuAPI tls.md](https://github.com/Doridian/OpenBambuAPI/blob/main/tls.md)

### Inferences
- Apache Commons Net `FTPSClient(true)` handles implicit FTPS. Known issue from background knowledge (UNVERIFIED): Bambu's FTPS server needs TLS session reuse on the data channel, which is awkward in Java/Android. Test this early.
- Start flow: upload `name.3mf` (likely to the root or /cache, unverified), then publish `project_file` with `param = Metadata/plate_N.gcode`. The 3mf must be sliced for that model.

### Gaps
- Upload directory, session-reuse requirement, and the exact `project_file` url string for FTP-uploaded files were not confirmed.

## 4. Discovery (SSDP)

### Takeaway
Printers broadcast SSDP NOTIFY to multicast 239.255.255.250:1900 (and reportedly also to ports 1990 and 2021). The `Location` is the IP, and `USN` is the serial.

### Cited Findings
- Example NOTIFY from a P1S (community capture): `NOTIFY * HTTP/1.1`, `HOST: 239.255.255.250:1900`, `Server: UPnP/1.0`, `Location: <ip>`, `NT: urn:bambulab-com:device:3dprinter:1`, `USN: <serial>`, `Cache-Control: max-age=1800`, `DevModel.bambu.com: C12`, `DevName.bambu.com`, `DevSignal.bambu.com: -61`, `DevConnect.bambu.com: lan`, `DevBind.bambu.com: free`, `Devseclink.bambu.com: secure`, `DevVersion.bambu.com: 01.07.00.00`, `DevCap.bambu.com: 1`. — [Gist comment, Jan 2025](https://gist.github.com/Alex-Schaefer/72a9e2491a42da2ef99fb87601955cc3)
- Model codes: C11 = P1P, C12 = P1S, C13 = X1E, N1 = A1 mini, N2S = A1. X1C appears as "3DPrinter-X1-Carbon". — same gist
- Reports say printers send from port 1900 to ports 1990 and 2021, and a client may need UDP 2021 open for M-SEARCH replies. — [search result summary of community notes](https://gist.github.com/Alex-Schaefer/72a9e2491a42da2ef99fb87601955cc3)

### Inferences
- On Android, acquire a `WifiManager.MulticastLock` and listen on a UDP socket joined to 239.255.255.250:1900 (and bind 2021 as fallback). The NOTIFY intervals may be long; always offer manual IP + serial + access code entry as well.

### Gaps
- H2D model code and whether H2D uses the same NT were not found. OpenBambuAPI has no SSDP page (my fetch of ssdp.md returned 404).
- Whether the printer answers M-SEARCH on 1900 is not confirmed.

## 5. Authorization Control / Developer Mode (2025, state in 2026)

### Takeaway
Since 2025 firmware, Bambu's "Authorization Control" blocks unofficial software from controlling the printer. Third-party LAN control works only if the printer is in LAN Only mode with Developer Mode enabled (which disables the authorization check). This costs cloud features.

### Cited Findings
- Rollout: X1 series in firmware 01.08.05.00 (March 2025); P-series 01.08.02.00 and A-series 01.05.00.00 (June 2025). After these, printers refuse unofficial software unless Developer Mode is on. — [All3DP summary via search](https://all3dp.com/4/bambu-labs-controversial-authorization-control-hits-budget-3d-printers/) (the page itself returned 403 to me; the figures come from the search summary, so verify them)
- Developer Mode is only available while in LAN-only mode and disables Authorization Control. Cloud features (Bambu Handy, remote printing) are lost. Bambu Connect cannot be used with a printer in Developer Mode. Bambu does not guarantee compatibility or support in this mode. Downgrading firmware is still possible per Bambu. — same All3DP summary
- Developer Mode keeps full MQTT control, FTP, and camera stream for slicers and tools; path is Settings, network, LAN Mode Only, Developer Mode. — [Tom's Hardware forum](https://forums.tomshardware.com/threads/bambu-lab-removes-third-party-app-support-for-p-and-a-series-3d-printers.3880920), [printer-hub.ru guide](https://printer-hub.ru/en/posts/bambu-lab-firmware-guide); All3DP guide updated 31 March 2026 also says Developer Mode depends on LAN Only ([All3DP FR](https://all3dp.com/fr/2/bambu-lab-securite-mode-lan-uniquement-mode-developpeur/))
- ha-bambulab is still actively developed (about 1,427 commits, 2.4k stars, 102 open issues, per the repo page). — [ha-bambulab](https://github.com/greghesp/ha-bambulab)
- SimplyPrint reports (via Manufactur3D, unconfirmed by me) that in May 2026 Bambu pressed the developer of an OrcaSlicer fork restoring cloud printing to remove it. — [SimplyPrint](https://simplyprint.io/ja/compatibility/bambu-lab)

### Inferences
- The app should require users to put the printer in LAN Only + Developer Mode and explain that cloud and Bambu Handy stop working. Cloud-mode control is not a realistic target.
- Without Developer Mode, the printer may allow read-only status via MQTT (from background knowledge; UNVERIFIED), but commands are blocked.

### Gaps
- Bambu wiki page (wiki.bambulab.com/en/software/third-party-integration) returned HTTP 402; I could not read the official wording.
- No 2026 firmware changelog confirming any further restriction. H2D developer-mode specifics are not confirmed.

## 6. MQTT libraries for Android/JVM

### Takeaway
Prefer HiveMQ MQTT Client (active, pure Java, Android 4.4+). Eclipse Paho Android service is reported as stale. Both can do TLS with a custom SSL socket factory/TrustManager.

### Cited Findings
- Commenters on the paho.mqtt.android GitHub issues call the repo dead and recommend HiveMQ MQTT Client or hannesa2's community Kotlin fork; one warns of Android 12+ problems. These are user comments, not official statements. — [paho.mqtt.android issue 500](https://www.github.com/eclipse/paho.mqtt.android/issues/500), [issue 493](https://www.github.com/eclipse/paho.mqtt.android/issues/493)
- HiveMQ support says the client runs on Android API 19+ and offers MQTT 5 and reactive/async/blocking APIs. — [HiveMQ community](https://community.hivemq.com/t/getting-started-with-the-hivemq-client-library/24)

### Inferences
- The printer broker speaks MQTT 3.1.1 (background knowledge; most Bambu clients use 3.1.1). HiveMQ's `Mqtt3` API or Paho core (non-Android) both work.
- Size/dependency counts (netty etc. in HiveMQ) were not measured. Check the APK size impact.

### Gaps
- Current release dates and commit activity of HiveMQ and Paho were not checked. Library size not measured.

## 7. Open-source references

### Cited Findings
- OpenBambuAPI (Doridian): GFDL docs on MQTT, FTP, video, TLS, G-code, cloud; 709 stars, 49 commits. — [repo](https://github.com/Doridian/OpenBambuAPI)
- ha-bambulab (greghesp): HA integration via HACS, MIT, docs at docs.page/greghesp/ha-bambulab. — [repo](https://github.com/greghesp/ha-bambulab)
- bambulabs_api (Python, MQTT-based; PyPI latest seen 2.3.3). Search found no confirmation of camera, FTP, or H2D support in it. — [PyPI](https://pypi.org/project/bambulabs-api/)
- Other: coelacant1/Bambu-Lab-Cloud-API covers cloud, MQTT, FTP and camera (control commands not yet implemented per its notes). — [GitHub](https://github.com/coelacant1/Bambu-Lab-Cloud-API)

### Inferences
- Best implementation references for edge cases (project_file URL, FTP session reuse, H2D fields) are the ha-bambulab source and bambulabs_api source, which I did not read.

### Gaps
- No source code of ha-bambulab or bambulabs_api was inspected; H2D support in them is unverified.
