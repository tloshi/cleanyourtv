# Sony BRAVIA 4K UR3: what was done, in order

2026-10-20, local time. Done by the owner with Claude Code from a Windows 11 PC on the same network,
over network ADB to `192.168.178.26:5555` (USB debugging was already on; the TV is on Ethernet).
Tools: platform-tools adb 1.0.41, the repository's `tools/`, Temurin JDK 21, Gradle 8.9.

The TV before and after, package by package: [`state-before.txt`](state-before.txt),
[`state-after.txt`](state-after.txt). Screens: [`screenshot-before.png`](screenshot-before.png),
[`screenshot-after.png`](screenshot-after.png). How to undo or change anything: [`README.md`](README.md).

## Connect and back up (nothing changed yet)

| Time | Step | Result |
|---|---|---|
| 09:10 | Found the TV in the ARP table (port 5555 open), `adb connect`, owner accepted "Allow USB debugging?" | BRAVIA 4K UR3 (BRAVIA_UR3_EU), Android TV 10, firmware QTG3.200305.006.S97 662301, MediaTek mt5893, 2.3 GB RAM, `ro.adb.secure=1` |
| 09:13 | Full snapshot: `tools/snapshot.py --save backup/2026-10-20-bravia-ur3-before` | 228 packages (4 disabled at the factory), Home `com.google.android.tvlauncher/.MainActivity`, 299 settings, getprop, input list. Kept locally only: it holds the TV's serial and android_id |
| 09:14 | `RESTORE.ps1 -DryRun` | 0 changes to undo |
| 09:16 | Undo proof: disabled `tv.samba.ssm`, ran `RESTORE.ps1` | Re-enabled, "the TV matches the snapshot", stock home unchanged |
| 09:20 | Checked who answers HOME and who owns the network | Only `tvlauncher` and Settings' `FallbackHome`; network owned by `android.uid.system` (setupwraith does not claim Home here) |
| 09:25 | Second undo route: `tools/recovery.py --save examples/sony-bravia-4k-ur3/debloat.txt` | Record in `~/.cleanyourtv/`; all 33 listed packages were enabled before |

## Debloat: 33 packages, `pm disable-user --user 0`, one per call, screenshot after each batch

| Time | Batch | Packages | Check |
|---|---|---|---|
| 09:27 | 1 tracking (9) | `tv.samba.ssm`, `com.qterics.da.product`, `com.sony.dtv.da.service`, `com.sonyericsson.idd.agent`, `com.sony.dtv.usercontext`, `com.sony.dtv.sonybugreportsys`, `com.google.android.feedback`, `com.google.android.tv.bugreportsender`, `com.google.android.partnersetup` | Home fine |
| 09:27 | 2 ads and promos (6) | `com.sony.dtv.promos`, `com.sony.dtv.sonyselect`, `com.sony.dtv.bivlinfo`, `com.google.android.tvrecommendations`, `android.autoinstalls.config.sony.bravia`, `com.sony.dtv.tvlaunchercustomizer` | The stock home lost its Inputs, notifications and timer icons (added by `tvlaunchercustomizer`). The remote's Input button still opened Sony's Inputs panel, so it stayed disabled |
| 09:28 | 3 demo, help, UK-only (6) | `com.sony.dtv.demosupport`, `com.sony.dtv.demosystemsupport`, `com.sony.dtv.multiscreendemo`, `com.sony.dtv.smarthelp`, `com.sony.dtv.customersupport`, `com.sony.dtv.youview` | Home fine |
| 09:28 | 4 preloaded, by choice (6) | `com.google.android.play.games`, `com.google.android.videos`, `com.google.android.backdrop`, `com.sony.dtv.livingfit`, `com.google.android.youtube.tv`, `com.google.android.youtube.tvmusic` | Home fine |
| 09:29 | 5 Miracast, by choice (2) | `com.mediatek.wfd`, `com.sony.dtv.networkapp.wifidirect` | AirPlay, Chromecast and HomeKit still enabled |
| 09:29 | 6 smart home and notices, by choice (4) | `com.sony.dtv.seeds.iot`, `com.sony.dtv.seeds.auth`, `com.sony.dtv.smarthomesettings`, `com.sony.dtv.notificationcenter` | AirPlay service still running |
| 09:30 | Smoke test | Live TV app, Netflix, Prime Video, SmartTube, MagentaTV, Disney+, Apple TV, Smart IPTV, Spotify each came to the front; voice, keyboard, remote services, Play Store and Services, HbbTV, system update, DLNA and Media Player still enabled | Pass |

The owner's choices behind batches 4 to 6: keep voice search, Chromecast, AirPlay/HomeKit, firmware
updates, DLNA, Netflix, Prime Video and Apple TV; official YouTube is never used (SmartTube is);
mirroring from Android never worked (Sony's `screnmirroring.com` was already disabled at the factory).
Media Player and Sony Music stay enabled because DLNA plays through them.

## Launcher

| Time | Step | Result |
|---|---|---|
| 09:32 | Built the launcher (`gradlew assembleRelease`; Gradle installed build-tools 34 itself) | 335 KB APK |
| 09:33 | `launcher\build-and-install.ps1 -TvHost 192.168.178.26` | Installed (md5 match), set as Home, started, then `com.google.android.tvlauncher` disabled; Home resolves to `com.gathin.tvhome/.MainActivity` |
| 09:33 | Pushed `theme/` with `tools\theme.ps1` | Dock SmartTube, Prime Video, MagentaTV, Disney+; Right, Down and Up checked with key presses |
| 09:34 | Inputs menu | The PlayStation 5 was listed twice: its HDMI-CEC input (`HDMI100004`) and its port HDMI 1 (`HW2`) |
| 09:37 | Launcher fix: an HDMI-CEC device is folded into its port's row (`InputPorts`, 4 unit tests; all 75 launcher tests and lint pass), reinstalled | One row, "HDMI 1 · PlayStation 5"; selecting it opened a session on `HDMI100004` |
| 09:38 | Live TV row | Sent a passthrough URI for the satellite tuner; Sony's TV app opened on the last input instead. The owner has no antenna, cable or satellite, so the row was removed (`"live_tv_package": ""`) |
| 09:53 | Layout for how the TV is used (owner: SmartTube for the kids, Netflix, PlayStation, Smart IPTV, films from USB) | 5 columns; row 1 SmartTube, Netflix, Prime Video, Apple TV, Disney+; row 2 Smart IPTV, Media Player, MagentaTV; Recordings, Timers, Vewd and Sony Music hidden |
| 09:54 | Reboot check | Uptime 82 s: Home is the launcher, 38 packages disabled (33 + 4 factory + stock launcher) |
| 10:05 | `examples\sony-bravia-4k-ur3\install.ps1` end to end | Build, install, Home kept, theme pushed |

Not done, on the owner's decision: ad-blocking Private DNS (Android has no fallback, so a resolver
outage would show as "No internet").

## Speed round (owner: "I thought this would make my TV faster")

| Time | Step | Result |
|---|---|---|
| 10:12 | Measured, TV on, 11 minutes up | 710 MB RAM available, swap full, 1.1 GB storage free. Largest: Netflix and SmartTube (cached from the smoke test), Vewd 83 MB (kept: Sony's AirPlay runs on it), voice assistant 55 MB (kept by choice) |
| 10:15 | Storage per app (`dumpsys diskstats`) | App caches 1.65 GB, 1.27 GB of it the Play Store; storage 81% full |
| 10:17 | Baseline: reboot, 2.5 minutes idle, screen on | 856 MB RAM available, swap 84% used, 390 processes (44 app processes), 1.27 GB free |
| 10:18 | Usage since 31 January (`dumpsys usagestats`, yearly) | 15 of the owner's apps never launched (Kodi once, in February). The iPhone's AirPlay uses Sony's receiver; AirScreen was never on screen or running this year |
| 13:05 | Copied the 15 apps' APKs and Kodi's data folder to `backup/` (local only; the owner said no backup is needed) | 420 MB of APKs, 73 MB of Kodi data |
| 13:09 | Uninstalled, owner's choice: KiKA, ARD Mediathek, RTL+, DAZN, Steam Link, Downloader, BrowseHere, AirScreen, OnStream, HiTV, ReVanced Manager, the two microG copies (`com.mgoogle.android.gms`, `app.revanced.android.gms`), Kodi, and one sideloaded build of the owner's own | All `Success`; MX Player kept on the owner's word |
| 13:10 | `settings put global window_animation_scale / transition_animation_scale / animator_duration_scale 0.5` | Menus and app openings animate twice as fast |
| 13:10 | `pm trim-caches 100G` (the same as clearing cached data in Settings) | Storage 40% full, 3.9 GB free |
| 13:10 | Crash log | Sony's DIAL service (`com.sony.dtv.webapi.core`) crashed once at 13:09:58 looking up AirScreen's activity, 4 s before Android announced its removal; it restarted by itself and has answered DIAL requests since. Earlier, at 12:50, the media scanner crashed once on a USB drive that mounted and vanished; nothing involved there was disabled |
| 13:16 | After: reboot, 2.5 minutes idle | 898 MB RAM available, swap full, 383 processes (43 app processes), 3.9 GB free, 0 crashes since boot. The TV came back in standby this time, so the RAM figures are not strictly comparable with the baseline (screen on) |

What this changed: three times the free storage, which Android needs for app updates and for its
own temporary files; snappier menus; fewer apps updating and starting in the background. Memory
barely moved: the apps that ran in the background were the ones the owner kept.

## Open when this was written

- Close network ADB: on the TV, Developer options > USB debugging off and revoke USB debugging
  authorisations, reboot; then `Test-NetConnection 192.168.178.26 -Port 5555` must fail. Not done yet.
- Checks only the real remote and devices can prove: the mic button, a film from a USB drive in Media
  Player, AirPlay from an iPhone, casting from a phone.
- Whether to build a single large SmartTube card above the rows (a new launcher feature) instead of the
  current first-tile layout.
