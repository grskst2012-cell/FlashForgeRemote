# Control protocols of FlashForge, Moonraker and OctoPrint printers (driver implementation notes)

Evidence legend: "Cited" = read on the linked page this session. All FlashForge material comes from unofficial community reverse-engineering docs (Parallel-7 wiki, GhostTypes libraries), not from the vendor. Moonraker, Klipper and OctoPrint material comes from official docs. Today: 2026-11. Nothing here was tested on hardware.

## 1. FlashForge TCP 8899 legacy protocol (commands, framing, upload)

### Takeaway
Port 8899 is unauthenticated ASCII over raw TCP. Every command starts with `~`, and the client must send `~M601 S1` first to take (exclusive) control. M28/M29 binary upload works only on legacy A3/A4-class printers; 5M-class models use HTTP `/uploadGcode`.

### Cited Findings
- Session: connect to 8899 (no authentication), send `~M601 S1`. Success reply is `CMD M601 Received.` / `Control Success V2.1.` / `ok`. A `Control Failed.` reply means another connection still holds control (or a prior session was not released). `~M602` releases control and must be sent before disconnecting (reply `Control Release.`). — [TCP Protocol](https://github.com/Parallel-7/flashforge-api-docs/wiki/TCP-Protocol), [M-Code Reference](https://github.com/Parallel-7/flashforge-api-docs/wiki/M-Code-Reference)
- Framing: commands start with `~` and end with CRLF; replies are multi-line text with `CMD Mxxx Received.` first and end with `ok`. — [TCP Protocol](https://github.com/Parallel-7/flashforge-api-docs/wiki/TCP-Protocol); the ff-5mp-api-ts docs say `\n` or `\r\n` is accepted — [protocols.md](https://raw.githubusercontent.com/GhostTypes/ff-5mp-api-ts/main/docs/protocols.md)
- Keep-alive: 5M series closes idle connections after about 30 s, so send something (e.g. `~M27`) at least every 20 s. Up to 10 concurrent connections are accepted, but control is exclusive in practice. — [TCP Protocol](https://github.com/Parallel-7/flashforge-api-docs/wiki/TCP-Protocol)
- `M115` reply lines: `Machine Type:`, `Machine Name:`, `Firmware: v3.1.5`, `SN:`, `X: 220 Y: 220 Z: 220`, `Tool Count: 1`, `Mac Address:XX:..` (no space after the colon; wiki notes a trailing space), then `ok`. — [TCP Protocol](https://github.com/Parallel-7/flashforge-api-docs/wiki/TCP-Protocol), [M-Code Reference](https://github.com/Parallel-7/flashforge-api-docs/wiki/M-Code-Reference)
- `M119` reply: `Endstop: X-max: 110 Y-max: 110 Z-min: 0`, `MachineStatus: READY`, `MoveMode: READY`, `Status: S:1 L:0 J:0 F:0`, `LED: 1`, `CurrentFile:`, `ok`. MachineStatus values: READY, BUILDING_FROM_SD, PAUSED, BUSY, BUILDING_COMPLETED. MoveMode values: MOVING, PAUSED, READY, WAIT_ON_TOOL, HOMING. — same two pages
- `M105` reply: modern `T0:17.9/0.0 T1:0.0/0.0 B:18.5/0.0` (current/target); legacy `T0:22 /0 B:11/0`. — [M-Code Reference](https://github.com/Parallel-7/flashforge-api-docs/wiki/M-Code-Reference)
- `M27` reply: `SD printing byte X/Y` then `Layer: x/y`, then `ok`. — [TCP Protocol](https://github.com/Parallel-7/flashforge-api-docs/wiki/TCP-Protocol)
- `M661` (file list): after `ok` comes one line starting `D::` with entries joined by `::` (e.g. `/data/File1.3mf::/data/File2.gcode`); needs custom parsing. `M662 <path>` returns `ok` followed by raw PNG bytes (the M-Code page mentions a custom header 0xa2a22a2a; the TCP page does not describe it). — [TCP Protocol](https://github.com/Parallel-7/flashforge-api-docs/wiki/TCP-Protocol), [M-Code Reference](https://github.com/Parallel-7/flashforge-api-docs/wiki/M-Code-Reference)
- Job control: `~M23 0:/user/<name>` (or `/data/<name>` on newer firmware) selects the file, reply `CMD M23 Received.` + `ok`; `M24` start/resume; `M25` pause; `M26` cancel; `M112` emergency stop. After a TCP `M26`, the printer stays uncontrollable until the cancel dialog on screen is cleared (on 5M/AD5X, HTTP `stateCtrl_cmd` / `setClearPlatform` clears it). Motion `G1` is ignored during a job. — [M-Code Reference](https://github.com/Parallel-7/flashforge-api-docs/wiki/M-Code-Reference), [TCP Protocol](https://github.com/Parallel-7/flashforge-api-docs/wiki/TCP-Protocol)
- Other codes: `M104 S<t>` nozzle, `M140 S<t>` bed, `M106 [S0-255]` fan (A3 on/off only), `M107`, `M144`/`M145` LED on/off (write EEPROM; avoid frequent use), `M146 r<0-255> g<0-255> b<0-255> F0` LED RGB (preferred), `M610` rename (restarts network service), `M17`/`M18` steppers, `M114` position, `M650`/`M663` misc. `G28` home. — [M-Code Reference](https://github.com/Parallel-7/flashforge-api-docs/wiki/M-Code-Reference), [protocols.md](https://raw.githubusercontent.com/GhostTypes/ff-5mp-api-ts/main/docs/protocols.md)
- Upload (legacy A3/A4 only): `~M28 <exact_size_bytes> 0:/user/<filename>` (firmware regex `^\s*(\d+)\s+(0:/user/([^\n]+))`). Reply `CMD M28 Received.` + `ok` (or `Error: Not enough space`). Then send exactly `<size>` raw bytes (no `~`, no escaping). Then `~M29`: size match gives a saved file and `ack: "ok"`; mismatch gives `File Is Not Aviable` (sic) and the temp file is deleted. Inactivity timeout resets per chunk (value not stated). No checksum or chunk size documented. — [TCP Protocol](https://github.com/Parallel-7/flashforge-api-docs/wiki/TCP-Protocol)
- Library layout: `FlashForgeA3Client`, `FlashForgeA4Client`, generic `FlashForgeClient` (legacy fallback), `FiveMClient` (HTTP plus TCP). `FiveMClient` uses TCP for manual axis `move`, detailed endstops and some filament loading. — [ff-5mp-api-ts](https://github.com/GhostTypes/ff-5mp-api-ts)

### Inferences
- A driver should open one TCP socket per printer, send `M601 S1`, poll `M27`/`M105`/`M119` (also acts as keep-alive), and always send `M602` on close (also on app backgrounding), or the next session will fail with `Control Failed`.
- Read-until-`ok` is the sensible framing rule; for `M661`/`M662`, keep reading after `ok` (text until timeout/line end; PNG until a length or idle timeout). The termination rule for these is not documented.

### Gaps
- Exact M661 format differences between legacy and 5M firmware (path prefix `0:/user/` vs `/data/`) are only partly documented.
- No source found for Guider 3 or Creator 3 TCP behaviour; the Parallel-7 TCP page says nothing on them. Assume Adventurer-3/4-style legacy protocol (inference, unverified).
- M662 PNG framing, M28 inactivity timeout, and whether M28/M29 work on 5M-class firmware (wiki says use HTTP) are not confirmed.

## 2. FlashForge HTTP API on 8898 (5M, 5M Pro, AD5X, Creator 5) and cameras

### Takeaway
All endpoints are `POST` JSON on port 8898 with `serialNumber` and `checkCode` in the body (upload: in headers). HTTP status is always 200; success is `code == 0`. Camera is MJPEG at `http://<ip>:8080/?action=stream`, advertised by `/detail.cameraStreamUrl`.

### Cited Findings
- Auth: `serialNumber` (starts with "SN") and `checkCode` (static code shown on the printer screen under Network/About, typically 4-5 digits). Body fields for JSON endpoints, HTTP headers for `/uploadGcode`. Boolean headers must be lowercase `"true"`/`"false"`. Error codes: `0` ok, `-1` bad parameters, `1` "SN is different" or "Access code is different", `-2` "Lan mode error" (LAN mode off), `2` busy and `3` file not found (`/printGcode`). TCP 8899 needs no credentials. — [Authentication](https://github.com/Parallel-7/flashforge-api-docs/wiki/Authentication), [HTTP REST API](https://github.com/Parallel-7/flashforge-api-docs/wiki/HTTP-REST-API)
- Endpoints: `/detail`, `/product`, `/control`, `/gcodeList`, `/gcodeThumb`, `/printGcode`, `/uploadGcode`. Thumbnail URLs in `/detail` look like `http://<ip>:8898/thumb/<file>`. — [HTTP REST API](https://github.com/Parallel-7/flashforge-api-docs/wiki/HTTP-REST-API)
- `/detail` response `detail` fields: `status` (`ready`, `busy`, `calibrate_doing`, `error`, `heating`, `printing`, `pausing`, `pause`, `canceling`, `cancel`, `completed`, `downloading`; FW 5.x adds `cloud_slicing`, `sending`, `unzipping`), `printProgress` (0.0-1.0 ratio), `printLayer`/`targetPrintLayer`, `estimatedTime` (remaining seconds; frozen while paused/heating), `printDuration` (elapsed), `platTemp`/`platTargetTemp`, `rightTemp`/`rightTargetTemp`/`leftTemp`/`leftTargetTemp`, `chamberTemp`, `chamberFanSpeed`/`coolingFanSpeed` (0-100 percent), `lightStatus` ("open"/"close"), `pid` (hex string; 35=5M, 36=5M Pro, 38=AD5X), `measure`, `cameraStreamUrl`, `printFileName`, `printFileThumbUrl`, `firmwareVersion`, `name`, `macAddr`, `ipAddr`, `errorCode`, `cumulativeFilament`, `cumulativePrintTime`, `remainingDiskSpace`, `currentPrintSpeed`, `printSpeedAdjust`, `zAxisCompensation`, `tvoc`, etc. AD5X adds `hasMatlStation` and `matlStationInfo` (`slotInfos` with `slotId`, `hasFilament`, `materialName`, `materialColor`). — [HTTP REST API](https://github.com/Parallel-7/flashforge-api-docs/wiki/HTTP-REST-API), [AD5X](https://github.com/Parallel-7/flashforge-api-docs/wiki/AD5X)
- `/control` body: `{serialNumber, checkCode, payload:{cmd, args}}`. Always returns `{code:0,message:"Success"}` even for unknown cmds. Commands: `lightControl_cmd {status:"open"|"close"}`; `jobCtl_cmd {jobID:"", action:"pause"|"continue"|"cancel"}`; `printerCtl_cmd {speed (50-500; 50-150 on 5M/Pro), zAxisCompensation (-5..5), chamberFan 0-100, coolingFan 0-100}` (omit fields to leave unchanged; `-200` = no change); `circulateCtl_cmd {internal, external: "open"|"close"}`; `streamCtrl_cmd {action:"open"|"close"}`; `stateCtrl_cmd {action:"setClearPlatform"}`; `temperatureCtl_cmd {rightNozzle 0-265, platform 0-100, leftNozzle, chamber}` (`-100` off, `-200` no change; 5M applies only rightNozzle and platform); `reName_cmd {name}`; `delayClose_cmd {automaticShutdown, shutdownAfterTime}`; `calibration_cmd {levelingDetection, vibrationCompensation}`; `userProfile_cmd`. AD5X extras: `msConfig_cmd`, `ms_cmd`, `moveCtrl_cmd {axis,delta}`, `extrudeCtrl_cmd {delta}`, `homingCtrl_cmd`, `errorCodeCtrl_cmd {action:"clearErrorCode", errorCode}`. — [HTTP REST API](https://github.com/Parallel-7/flashforge-api-docs/wiki/HTTP-REST-API), [AD5X](https://github.com/Parallel-7/flashforge-api-docs/wiki/AD5X), [Adventurer 5M Series](https://github.com/Parallel-7/flashforge-api-docs/wiki/Adventurer-5M-Series)
- `/gcodeList`: returns the 10 most recently used files; `gcodeList` (names) on 5M/5M Pro/Creator 5, `gcodeListDetail` (with `printingTime`, `totalFilamentWeight`, `gcodeToolCnt`, `gcodeToolDatas`) on AD5X. `/gcodeThumb {fileName}` returns Base64 `imageData`. — [HTTP REST API](https://github.com/Parallel-7/flashforge-api-docs/wiki/HTTP-REST-API)
- `/printGcode`: `{serialNumber, checkCode, fileName, levelingBeforePrint:bool}` (both required). AD5X adds `firstLayerInspection`, `flowCalibration`, `timeLapseVideo`, `useMatlStation`, `gcodeToolCnt` (1-4), `materialMappings[{toolId (0-based), slotId (1-based), materialName, toolMaterialColor "#RRGGBB", slotMaterialColor}]`; 5M firmware ignores the AD5X fields. — [HTTP REST API](https://github.com/Parallel-7/flashforge-api-docs/wiki/HTTP-REST-API)
- `/uploadGcode`: `multipart/form-data`, file part named `gcodeFile` (`application/octet-stream`). Headers: `serialNumber`, `checkCode`, `fileSize`, `printNow`, `levelingBeforePrint`; AD5X adds `flowCalibration`, `useMatlStation`, `firstLayerInspection`, `timeLapseVideo`, `gcodeToolCnt`, and `materialMappings` as Base64 of the JSON array. — [HTTP REST API](https://github.com/Parallel-7/flashforge-api-docs/wiki/HTTP-REST-API)
- `/product` capability flags are documented as unreliable; detect the model by `pid` instead. `lightControl_cmd` often works even when `lightCtrlState` is 0. — [HTTP REST API](https://github.com/Parallel-7/flashforge-api-docs/wiki/HTTP-REST-API)
- Camera: MJPEG on port 8080 (`http://<ip>:8080/?action=stream`); base 5M has no built-in camera (`cameraStreamUrl` empty), 5M Pro has one. — [Adventurer 5M Series](https://github.com/Parallel-7/flashforge-api-docs/wiki/Adventurer-5M-Series), [HTTP REST API](https://github.com/Parallel-7/flashforge-api-docs/wiki/HTTP-REST-API)
- Creator 5 / 5 Pro report TCP port 8899 in discovery but run no TCP server (HTTP only). — [Discovery Protocol](https://github.com/Parallel-7/flashforge-api-docs/wiki/Discovery-Protocol)
- Supported by ff-5mp-api-ts: Adventurer 5M, 5M Pro, AD5X (HTTP+TCP); Adventurer 3 and 4 (TCP clients). Related: FlashForge-Python-API (`ff-5mp-api-py`). — [ff-5mp-api-ts](https://github.com/GhostTypes/ff-5mp-api-ts)

### Inferences
- Because success is signalled only in `code`, the HTTP layer must parse JSON even on HTTP 200 and map `-2`/`1` to "enable LAN mode" / "wrong check code" UI messages.
- Treat unknown `status` strings as busy (the wiki says this too). Use `pid` to choose per-model capability tables.
- For pause/resume/cancel on 5M-class printers prefer HTTP `jobCtl_cmd` over TCP `M25/M26` to avoid the cancel-dialog lockout.
- Guider 3 / Creator 3 camera URLs are unknown; try `cameraStreamUrl` from `/detail` where HTTP exists, otherwise let the user enter a URL.

### Gaps
- No documentation found for Guider 3 or Creator 3 (neither protocol nor camera). Not on the Parallel-7 wiki pages fetched.
- Response body shape of `/gcodeList` for older firmware, and the exact FW versions for AD5X upload header variants ("firmware dependent" per wiki), are unverified.
- I did not read the library source; endpoints come from the wiki. The ff-5mp-api-ts `protocols.md` was less detailed than the wiki and says only that credentials go "in the request body or headers".

## 3. FlashForge UDP discovery

### Takeaway
Modern printers (5M, 5M Pro, AD5X, Creator 5) answer a probe sent to broadcast `255.255.255.255:48899` or multicast `225.0.0.9:19000`; legacy A3/A4 Pro answer multicast `225.0.0.9:8899`. The probe payload may be arbitrary; reply size decides the format (140 bytes legacy; 276/280 modern).

### Cited Findings
- Table: Modern 225.0.0.9:19000 and 255.255.255.255:48899 (276 or 280-byte reply); legacy 225.0.0.9:8899 (140-byte reply). "Probes can carry any payload." — [Discovery Protocol](https://github.com/Parallel-7/flashforge-api-docs/wiki/Discovery-Protocol)
- Modern reply layout (big-endian): 0x00 name (128 B, NUL-terminated); 0x84 u16 command (TCP) port; 0x86 u16 VID (typ. 0x2B71); 0x88 u16 PID; 0x8A u16 status (0 ready, 1 busy, 2 error); 0x8C u16 product type (0x5A02 on 5M platform); 0x8E u16 HTTP/event port (typ. 8898); 0x92 serial (128 B, NUL-terminated); trailing bytes after 0x112. FW 3.x = 276 bytes, FW 5.x = 280 bytes. Creator 5/5 Pro always 276. — [Discovery Protocol](https://github.com/Parallel-7/flashforge-api-docs/wiki/Discovery-Protocol)
- Legacy reply (140 B): name, port at 0x84, VID 0x86, PID 0x88 (e.g. 0x001D), status 0x8A; no serial (get it via TCP `~M115`). — same page
- Parsing rule: length >= 196 parse as modern (accept 276 and 280); exactly 140 parse as legacy. Deduplicate by IP or serial. AD5X shares product type 0x5A02 with the 5M, so use PID. — same page
- ff-5mp-api-ts exposes `PrinterDiscovery`. — [ff-5mp-api-ts](https://github.com/GhostTypes/ff-5mp-api-ts)

### Inferences
- On Android, sending a UDP datagram to 255.255.255.255:48899 needs the Wi-Fi interface and a `MulticastLock` (multicast groups), and may need the directed broadcast address of the subnet on some devices.
- Short arbitrary probe bytes (e.g. a few zero bytes) are likely adequate per the wiki, but the exact bytes other apps send were not found.

### Gaps
- Exact probe bytes used by the official FlashPrint/Orca-Flashforge clients not found.
- Whether Guider 3 / Creator 3 / Adventurer 4 (non-Pro) respond to these ports is unverified.

## 4. Moonraker API

### Takeaway
Everything is available both as HTTP and as JSON-RPC 2.0 over `ws://host:port/websocket`. Use the websocket for `printer.objects.subscribe` and notifications; use HTTP for uploads, downloads and one-off calls. All below are cited from the official docs (API version in docs "latest").

### Cited Findings
**Transport and conventions** — [Introduction](https://moonraker.readthedocs.io/en/latest/external_api/introduction/)
- WebSocket `ws://host:port/websocket` (JSON-RPC 2.0: `jsonrpc`, `method`, `params`, `id`); also `POST /server/jsonrpc`; Klippy bridge `ws://.../klippysocket`. HTTP success body is `{"result": ...}`; body args override query args; query type hints like `seconds:int=120`.

**Identify / auth** — [Server API](https://moonraker.readthedocs.io/en/latest/external_api/server/), [Authorization](https://moonraker.readthedocs.io/en/latest/external_api/authorization/)
- `server.connection.identify` (websocket only, once per connection): `client_name`, `version`, `type` (web|mobile|desktop|display|bot|agent|other), `url` required; optional `access_token`, `api_key`. Result `connection_id`.
- Auth methods: JWT `Authorization: Bearer` (1 h expiry; `/access/login` with `username`,`password`; `/access/refresh_jwt`), `X-Api-Key` header (`GET/POST /access/api_key`), trusted clients (by IP/domain, no credentials; `GET /access/info` returns `login_required` and `trusted`), oneshot token `GET /access/oneshot_token` (single use, 5 s lifetime, pass as `?token=`), useful for websocket URLs and file downloads/webcam where headers cannot be set. `GET /access/info` is unauthenticated.

**Status** — [Printer API](https://moonraker.readthedocs.io/en/latest/external_api/printer/)
- `GET /printer/info` (state, state_message, software_version...), `GET /printer/objects/list`, `POST /printer/objects/query` with `{"objects":{"extruder":["temperature","target"],"toolhead":null}}` returning `{eventtime,status}`; `printer.objects.subscribe` (websocket only; same params; new subscribe replaces the old; empty object cancels); updates arrive as `notify_status_update`. Missing objects/attributes are silently omitted. `GET /server/info` gives `klippy_state` (disconnected|startup|ready|error|shutdown) and `klippy_connected`. — [Server API](https://moonraker.readthedocs.io/en/latest/external_api/server/)
- Notifications (26 listed): `notify_status_update [object, eventtime]`, `notify_gcode_response [string]`, `notify_klippy_ready|shutdown|disconnected`, `notify_filelist_changed`, `notify_history_changed`, `notify_job_queue_changed`, `notify_update_response`, `notify_update_refreshed`, `notify_proc_stat_update`, `notify_cpu_throttled`, `notify_webcams_changed`, `notify_service_state_changed`, `notify_sensor_update`, etc. (`notify_power_changed` is NOT in the list fetched — power state changes appear to be delivered otherwise; unverified). — [JSON-RPC notifications](https://moonraker.readthedocs.io/en/latest/external_api/jsonrpc_notifications/)
- Klipper object fields to subscribe to: `print_stats` (filename, total_duration, print_duration, filament_used, message, info.total_layer/current_layer; `state` values not listed on that page), `display_status` (progress from M73, message), `virtual_sdcard` (progress, file_position, is_active), `extruder`/`heater_bed` (temperature, target, power), `fan`/`heater_fan` (speed 0-1), `gcode_move` (`speed_factor` M220, `extrude_factor` M221, position, homing_origin), `toolhead` (homed_axes, position), `idle_timeout.state`, `webhooks.state`, `pause_resume.is_paused`, `bed_mesh` (profile_name, probed_matrix, mesh_min, mesh_max, profiles), `gcode_macro <name>` variables. — [Klipper Status Reference](https://www.klipper3d.org/Status_Reference.html)
- Commands: `POST /printer/print/start?filename=`, `/printer/print/pause`, `/resume`, `/cancel`; `POST /printer/gcode/script` with `{"script":"M220 S120\nM221 S95"}`; `POST /printer/emergency_stop`; `POST /printer/restart`; `POST /printer/firmware_restart`; `GET /printer/query_endstops/status`. Sending `M112` via gcode script is queued behind other commands; use `emergency_stop`. Klippy generally must be `ready`. — [Printer API](https://moonraker.readthedocs.io/en/latest/external_api/printer/)
- Macros: discover with `printer.objects.list` entries named `gcode_macro <NAME>`; run via gcode script (the macro-run-by-script part is the standard mechanism; the docs fetched do not spell out a macro endpoint).
- Temperature store: `GET /server/temperature_store?include_monitors=false` returns per sensor `temperatures`, `targets`, `speeds`, `powers` arrays (1 s samples, max 1200). `GET /server/gcode_store?count=`. — [Server API](https://moonraker.readthedocs.io/en/latest/external_api/server/)

**History** — [History API](https://moonraker.readthedocs.io/en/latest/external_api/history/)
- `GET /server/history/list?limit=50&start=0&since=&before=&order=desc` returns `{count, jobs[]}`; job fields `job_id`, `user`, `filename`, `exists`, `status` (in_progress, completed, cancelled, error, klippy_shutdown, klippy_disconnect, interrupted), `start_time`, `end_time`, `print_duration`, `total_duration`, `filament_used` (mm), `metadata`, `auxiliary_data`. `GET /server/history/totals` returns `job_totals{total_jobs,total_time,total_print_time,total_filament_used,longest_job,longest_print}` and `auxiliary_totals`. Also `/server/history/job?uid=` (GET/DELETE), `POST /server/history/reset_totals`.

**Files / metadata / thumbnails** — [File Manager](https://moonraker.readthedocs.io/en/latest/external_api/file_manager/)
- `GET /server/files/list?root=gcodes`; `GET /server/files/metadata?filename=` (size, modified, slicer, estimated_time, filament_type, `thumbnails[{width,height,size,relative_path}]` where `relative_path` is relative to the gcode file's folder); `GET /server/files/thumbnails?filename=` (`thumbnail_path` relative to the gcodes root); download `GET /server/files/{root}/{path}` (thumbnail image fetch uses the same, e.g. `/server/files/gcodes/<dir>/<relative_path>`); upload `POST /server/files/upload` multipart with `file`, optional `root`, `path`, `checksum` (SHA256), `print` (true starts the print), returns 201; delete `DELETE /server/files/{root}/{path}`; directories `/server/files/directory`.

**Job queue** — [Job Queue](https://moonraker.readthedocs.io/en/latest/external_api/job_queue/)
- `GET /server/job_queue/status` (`queued_jobs[{filename,job_id,time_added,time_in_queue}]`, `queue_state` ready|loading|starting|paused); `POST /server/job_queue/job {filenames:[...], reset}`; `DELETE /server/job_queue/job?job_ids=` or `all=true`; `POST /server/job_queue/pause`, `/start`, `/jump?job_id=`.

**Power, update, machine, webcams** 
- Power: `GET /machine/device_power/devices` (`device`, `status`, `locked_while_printing`, `type`), `GET .../device?device=`, `POST .../device {device, action:on|off|toggle}`, batch `status|on|off`. — [Devices](https://moonraker.readthedocs.io/en/latest/external_api/devices/)
- Update manager: `GET /machine/update/status`, `POST /machine/update/refresh {name}`, upgrade via `POST /machine/update/upgrade {name}` (older per-component endpoints deprecated in API 1.5.0), `POST /machine/update/recover {name, hard}`, `/rollback`. Returns 503 during print. — [Update Manager](https://moonraker.readthedocs.io/en/latest/external_api/update_manager/)
- Host: `GET /machine/system_info`, `POST /machine/reboot|shutdown`, `POST /machine/services/restart {service}`, `GET /machine/proc_stats`, `POST /server/restart` (Moonraker only). — [Machine](https://moonraker.readthedocs.io/en/latest/external_api/machine/)
- Webcams: `GET /server/webcams/list` returns `webcams[]` with `name, location, service, enabled, icon, target_fps, target_fps_idle, stream_url, snapshot_url, flip_horizontal, flip_vertical, rotation, aspect_ratio, extra_data, source, uid`. `stream_url`/`snapshot_url` may be relative (e.g. `/webcam/?action=stream`) and must be resolved against the Moonraker host. — [Webcams](https://moonraker.readthedocs.io/en/latest/external_api/webcams/)

### Inferences
- Android driver: one OkHttp WebSocket; on open send `server.connection.identify` (type `mobile`, include `api_key` if configured), then `printer.objects.list`, then one `printer.objects.subscribe`; fall back to HTTP polling of `/printer/objects/query` every 1-2 s if the socket drops. Add HTTP `X-Api-Key` header on every request; for websocket without header support use `?token=` oneshot.
- Fans: `M106 S<0-255>` via gcode script for the part fan; `M220 S<pct>` speed and `M221 S<pct>` flow, read back from `gcode_move.speed_factor` / `extrude_factor`. These are standard Klipper gcodes, not read from the Moonraker pages fetched here.
- Bed mesh display: subscribe `bed_mesh` and render `probed_matrix`.
- Most typical home setup is trusted-clients (LAN CIDR) with no key; the driver should call `/access/info` first to decide whether credentials are required.

### Gaps
- Klipper `print_stats.state` value list (standby, printing, paused, complete, cancelled, error) was not on the fetched Status Reference page; widely known but not verified here.
- Macro listing/execution has no dedicated Moonraker endpoint in the pages I read.
- `/machine/update/upgrade` parameters only inferred from a note on the update page.

## 5. OctoPrint REST API (possible third driver)

### Takeaway
Plain REST with `X-Api-Key` (or `Authorization: Bearer <key>`); live status comes from a SockJS push channel (no SSE found in the docs). Keys can be obtained with the Application Keys workflow.

### Cited Findings
- Auth: `X-Api-Key` header, `Authorization: Bearer <key>`, or `?apikey=` (testing only). Missing/invalid keys give 403 when access control is on; when access control is off, unauthenticated requests are treated as admin. CORS requires `allowCrossOrigin` and still needs the key (irrelevant to a native app). — [General API](https://docs.octoprint.org/en/master/api/general.html)
- App Keys workflow: `GET /plugin/appkeys/probe` (204 = available), `POST /plugin/appkeys/request {app, user?}` returns 201 with `Location` poll URL and `app_token`; poll `GET /plugin/appkeys/request/<app_token>` about every second (202 pending, 200 with `api_key`, 404 denied/timeout; request dropped if unpolled for 5 s). The user approves in the OctoPrint web UI. — [AppKeys](https://docs.octoprint.org/en/master/bundledplugins/appkeys.html)
- `GET /api/job`: `job{file{name,origin,size,date},estimatedPrintTime,filament}`, `progress{completion,filepos,printTime,printTimeLeft}`, `state` text, optional `error`. `POST /api/job {"command":"start|restart|pause|cancel","action":"pause|resume|toggle"}` returns 204; 409 on state conflicts. — [Job API](https://docs.octoprint.org/en/master/api/job.html)
- `GET /api/printer` (409 if not operational): `temperature{tool0,bed:{actual,target,offset}}`, `sd`, `state{text,flags{operational,paused,printing,cancelling,pausing,sdReady,error,ready,closedOrError}}`. Control: `POST /api/printer/printhead` (jog/home/feedrate 50-200), `/api/printer/tool` (target/offset/select/extrude/flowrate 75-125), `/api/printer/bed`, `/api/printer/chamber`, `POST /api/printer/command {command|commands[]}` returns 204. — [Printer API](https://docs.octoprint.org/en/master/api/printer.html)
- Files: `GET /api/files/{local|sdcard}?recursive=true`; upload `POST /api/files/local` multipart (`file`, `path`, `select`, `print`, `userdata`); `POST /api/files/{loc}/{path} {"command":"select","print":true}`; `DELETE` returns 204 (409 if printing). The docs page shows no thumbnails field (thumbnails depend on plugins). — [Files API](https://docs.octoprint.org/en/master/api/files.html)
- Push: SockJS, message types `connected`, `reauthRequired`, `current`, `history`, `event`, `slicingProgress`, `plugin`; client sends `auth` (`userid:session` from `POST /api/login` passive login), optional `subscribe`, and `throttle` (base 500 ms). — [Push API](https://docs.octoprint.org/en/master/api/push.html)
- `POST /api/login` returns a `session` key usable for the push `auth`. — [General API](https://docs.octoprint.org/en/master/api/general.html)

### Inferences
- A mobile driver can skip SockJS and poll `GET /api/job` plus `GET /api/printer` every 2-3 s; this is simpler than implementing the SockJS framing.
- The SockJS endpoint path is conventionally `/sockjs/` (not stated on the fetched push page, unverified here).

### Gaps
- Webcam URL: not in the fetched pages (commonly configured in settings: `GET /api/settings` `webcam.streamUrl`; unverified here).
- Connection API (`/api/connection`) and version endpoint pages were not read.
- No SSE endpoint was found in OctoPrint docs; I did not look for plugins offering one.
