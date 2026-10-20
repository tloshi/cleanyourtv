# Sony BRAVIA 4K UR3: what was done, and how to undo or change it

Done on 2026-10-20 over network ADB (TV at `192.168.178.26`, Ethernet). Every command below is
PowerShell, run from the repository folder `C:\Users\taula\projects\tloshi\cleanyourtv`.

Every step in order, with its result: [`LOG.md`](LOG.md). Every package before and after:
[`state-before.txt`](state-before.txt), [`state-after.txt`](state-after.txt).

## What changed

- **33 packages disabled**, never uninstalled: tracking (Samba TV, Sony/Qterics/IDD agents), ads and
  promos (My BRAVIA, Sony Select, Google recommendation rows, partner auto-installs), the shop demo,
  help tiles, Play Games, Play Movies, official YouTube, YouTube Music, Miracast and the Alexa
  smart-home services. The full list with reasons: [`debloat.txt`](debloat.txt).
- **New home screen**: the CleanYourTV launcher (`com.gathin.tvhome`) is Home, and the stock launcher
  (`com.google.android.tvlauncher`) is disabled.
- **Kept on purpose**: voice search on the remote, Chromecast, AirPlay and Apple Home, firmware updates,
  DLNA, Media Player (USB films), Netflix, Prime Video, Apple TV, and every app you installed yourself.

Nothing else was touched: no settings, no DNS, no app data.

## Undo everything

Both undo routes need **USB debugging switched on** on the TV first: Settings > Device Preferences >
Developer options > USB debugging. Accept "Allow USB debugging?" on the TV if it asks.

**Back to exactly how the TV was before** (every package, the stock home screen, the new launcher
removed). This replays the full snapshot in `backup\2026-10-20-bravia-ur3-before\`, which only exists
on this PC; keep a copy of that folder somewhere safe:

```powershell
powershell -ExecutionPolicy Bypass -File backup\2026-10-20-bravia-ur3-before\RESTORE.ps1 -DryRun
```

```powershell
powershell -ExecutionPolicy Bypass -File backup\2026-10-20-bravia-ur3-before\RESTORE.ps1
```

The first command only lists what would change; the second does it and ends with "the TV matches the
snapshot". If the TV got a new IP address, add `-TvHost <new ip>`.

**Undo only the debloat list** (second route, from the record in `C:\Users\taula\.cleanyourtv\`):

```powershell
powershell -ExecutionPolicy Bypass -File tools\restore.ps1 -TvHost 192.168.178.26 examples\sony-bravia-4k-ur3\debloat.txt
```

## Re-enable one app

**On the TV, no computer needed**: Settings (the pill at the top right) > Apps > See all apps > Show
system apps > pick the app > Enable.

**Over ADB**, with USB debugging on:

```powershell
adb connect 192.168.178.26:5555
```

```powershell
adb -s 192.168.178.26:5555 shell pm enable com.google.android.youtube.tv
```

Feature you want back, and the packages to enable:

| Feature | Packages |
|---|---|
| Official YouTube (the remote's YouTube button) | `com.google.android.youtube.tv` |
| YouTube Music | `com.google.android.youtube.tvmusic` |
| Mirroring from Android phones and Windows (Miracast) | `com.mediatek.wfd`, `com.sony.dtv.networkapp.wifidirect`, `screnmirroring.com` (the last was already off before) |
| Alexa / smart-home control | `com.sony.dtv.seeds.iot`, `com.sony.dtv.seeds.auth`, `com.sony.dtv.smarthomesettings` |
| BRAVIA notifications | `com.sony.dtv.notificationcenter` |
| Ambient mode / Living Decor screensavers | `com.google.android.backdrop`, `com.sony.dtv.livingfit` |
| Help guide | `com.sony.dtv.smarthelp`, `com.sony.dtv.customersupport` |
| The stock home screen | `com.google.android.tvlauncher`, then hold OK on the Settings pill > Launcher settings > default home, and pick it in Android's Home picker (or run the full restore) |

## Change the dock, the rows or the wallpaper

The layout lives in [`theme\config.json`](theme/config.json). Edit it, then push it; no rebuild needed:

```powershell
powershell -ExecutionPolicy Bypass -File tools\theme.ps1 -TvHost 192.168.178.26 examples\sony-bravia-4k-ur3\theme
```

- **`dock_apps`**: the order of the first tiles. The first `columns` entries sit in the dock band (row 1),
  the rest start row 2 in the same order; every other app follows alphabetically. Package names of the
  apps on the TV: `adb -s 192.168.178.26:5555 shell pm list packages -3` (apps you installed).
- **`columns`**: tiles per row (4 to 7 is comfortable; tiles shrink from 5 up).
- **`rows_below_dock`**: grid rows visible on Home under the dock (0 to 3).
- **`hidden_apps`**: tiles never shown (the app stays installed). Recordings, Timers, Vewd and Sony
  Music are hidden; delete a line to bring a tile back.
- **Wallpaper**: put a dark 1920x1080 JPEG under about 300 KB into the `theme` folder as
  `wallpaper.jpg`, add `"wallpaper": true` to `config.json`, and push the theme again. Remove the file
  (or set `false`) and push to go back to black.

Back to the launcher's built-in defaults: `tools\theme.ps1 -TvHost 192.168.178.26 -Reset`. On the TV,
holding OK on the Settings pill opens Launcher settings (apps per row, animations).

## Reinstall or update the launcher

```powershell
powershell -ExecutionPolicy Bypass -File examples\sony-bravia-4k-ur3\install.ps1
```

It builds the launcher (needs the JDK and Android SDK that are on this PC), installs it, keeps it as
Home and pushes this theme.

## Things worth knowing

- **After a Sony firmware update**, an updated system app can come back enabled. Check with
  `adb -s 192.168.178.26:5555 shell pm list packages -d` (38 lines: 33 from the list, 4 that Sony ships
  disabled, and the stock launcher) and re-apply the list with
  `powershell -ExecutionPolicy Bypass -File tools\disable.ps1 -TvHost 192.168.178.26 examples\sony-bravia-4k-ur3\debloat.txt`.
- **Inputs**: the pill lists HDMI 1 to 4 and AV; a device that announces itself over HDMI-CEC is named
  in its port's row (HDMI 1 · PlayStation 5). The remote's Input button still opens Sony's own panel.
- **No ad-blocking DNS** was set: Android's Private DNS has no fallback, so a resolver outage shows as
  "No internet" although the connection is fine.
- **Security**: switch USB debugging off when you are not changing anything. Network ADB on this TV
  follows that switch (`persist.adb.tcp.port` is 5555), and port 5555 is unencrypted.
