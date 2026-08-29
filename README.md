# 📦 OBB Installer 2026 PRO MAX

### Install Android games (APK + OBB) without root, PC or Shizuku — the OBB rides inside the APK and lands in the right folder on first launch.

[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Android 11+](https://img.shields.io/badge/Android-11%2B-3DDC84?logo=android&logoColor=white)](#compatibility)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack-Compose-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![No Root](https://img.shields.io/badge/Root-Not%20Required-brightgreen)](#)
[![No Shizuku](https://img.shields.io/badge/Shizuku-Not%20Required-brightgreen)](#)

---

<p align="center">
  <strong>🇬🇧 English</strong> ·
  <a href="README_zh-CN.md">简体中文</a>
</p>

---

## 🔄 Secondary development notice

This repository is a **secondary development** of
[`aciderix/APK-OBB-HELPER`](https://github.com/aciderix/APK-OBB-HELPER/),
with the original architecture, concept and MIT license preserved. If you
redistribute this project, please keep this notice.

---

## 📜 Changelog

### v2.2 · 2026-08-29 — Ad-remover toolbox

- **Ad-remover toolbox** — the ApkAdRemover ad-removal engine is now an
  in-app tool (Tools tab): removes ads, disables signature verification
  (normal / original-package modes), patches Flutter `libapp.so`, and
  optimizes DEX, with toggles for sign mode, skip re-sign, output dir and
  pattern file.
- **Unified output directory** — processed APKs, the `ad_patterns.json`
  config and subscription files now default to `Download/OBBInstaller/`.
- **Export fallback** — results are saved next to the original APK, else via
  SAF, else through MediaStore into `Download/OBBInstaller/` when no storage
  permission is granted.
- **Memory & log fixes** — the processing log is capped so it cannot grow
  without bound; invalid subscription tokens now show a clear error instead
  of a false success.
- **About / Help** — new collapsible toolbox, features, privacy and disclaimer
  sections.

### v2.1 · 2026-08-28 — 34 languages & stability fixes

- **34 UI languages** (was 10), following the system locale.
- **Fixed signature check** — the hub keystore was loaded as JKS instead of
  PKCS12, so signature mismatches were never detected; now same/newer-version
  reinstalls warn you to uninstall the old app first.
- **Fixed bundled-mode crash** — reading a compressed bundled APK/OBB threw an
  `IOException`; now it degrades gracefully and installs.
- **Fixed install hang** — a lost `PackageInstaller` broadcast left the UI
  stuck on "Installing…"; now a 10-minute timeout returns a clear error.
- **Fixed export path message** — MediaStore renames duplicate files, so
  "Saved to…" now shows the real file name on disk.

### v2.0 · 2026-08-28 — split APKs, dual OBB, installed-game detection

- **Split APK support** — base + splits patched, re-signed and installed in one
  `PackageInstaller` session.
- **Dual OBB** — bundles both `main.*.obb` and an optional `patch.*.obb`.
- **16 KB page alignment** — bumps `p_align` on already-aligned ELF64 libs for
  Android 15+ devices.
- **Installed-game detection** — warns on same/newer installed version and on
  signature mismatch before installing.
- **Export patched APK** — saves the re-signed APK to `Download/OBBInstaller/`.
- **Open OBB folder / uninstall** buttons on the Done screen.
- **Error log copy & share**.
- **Install history** — last 20 installs, per-package deduplicated.
- **7 new UI languages.**

### v1.1 · 2026-08-28

- OBB files renamed to the standard `main.*.obb` / `patch.*.obb` format so any
  filename is recognized by the game.
- Native library loading fixed for Android 13+ W^X enforcement.

### v1.0 · 2026-08-27

- Initial release: single-tap APK + OBB install.

---

## 🤔 What is this?

**OBB Installer** is a tiny Android app that installs games shipped as
**APK + OBB** in one tap. On Android 11+, third-party apps can no longer copy
files into `Android/obb/<package>/`, so this app takes a different route: it
**embeds the OBB inside a patched copy of the APK** and lets the game itself
unpack it on first launch — from the game's own process, where Android still
allows writing to the OBB folder.

> **One tap. APK + OBB go in. The game finds its data and runs.**

## ✨ Features

- 🪄 **Single-tap install** — pick an APK and an OBB, hit *Install*, done.
- 📦 **No root / no PC / no Shizuku / no developer mode**.
- 📐 **16 KB page alignment** — bumps `p_align` on aligned ELF64 libs so games
     run on Android 15+ devices that enforce 16 KB memory pages.
- 🗂️ **Dual OBB** — bundles both `main.*.obb` and an optional `patch.*.obb`.
- 🧩 **Split APK support** — pick base APK + split APKs together; every split
     is patched, re-signed with the same key and installed in one session.
- 🛠️ **Auto-fixes legacy games** — bumps `targetSdkVersion` and patches old
     `.so` libraries (text relocations) so they install and load on modern Android.
- 🌐 **Multilingual UI** — 34 languages (English / 简体中文 / Français /
     Deutsch / Español / Português / 日本語 / Italiano / 한국어 / Русский /
     العربية / हिन्दी / עברית / …), follows system locale.
- 🔒 **Offline-first** — no telemetry, no ads.
- 📦 **Bundled mode** — drop a `.apk` + `.obb` into `app/src/main/assets/` to
     build a one-shot installer for a specific game.

## ⚡ Quick Start

1. Download **`app-release.apk`** from the
   [Releases page](https://github.com/Adam01405/obb-installer-2026-pro-max/releases).
2. Allow installs from unknown sources (the app guides you).
3. Pick the game's APK (select the split APKs too, if any), then the
   `main.*.obb` and optionally `patch.*.obb`.
4. Tap **Install APK + OBB** and confirm Android's prompt.
5. Launch the game — **first launch unpacks the OBB** (~30 s per GB), later
   launches are instant.

## 📱 Compatibility

| Scenario | Status |
|---|---|
| Stock / OEM Android 11–16 | ✅ Works |
| Single-player / offline games | ✅ Work out of the box |
| Legacy games (target SDK ≤ 23, old `.so` libs) | ✅ Auto-patched |
| Games with in-code signature checks | ⚠️ Refuse to run (rare) |
| Online competitive / live-service games | ❌ Anti-cheat rejects the patched signature |

## 🏗️ How it works

1. Patch the binary manifest: bump `targetSdkVersion`, inject a bootstrap
   `<provider>`.
2. Inject the bootstrap dex and the OBB(s) as STORED assets.
3. Patch every `lib/**/*.so`: fix text relocations, drop RWX segments, bump
   `p_align` to `0x4000` when already 16 KB aligned.
4. Re-sign with `apksig` (v1+v2+v3), install via `PackageInstaller` (base +
   splits in one session).
5. On the game's first launch, the injected provider copies the OBB into
   `Android/obb/<package>/` using the game's own UID.

## 🔧 Build from source

```bash
git clone https://github.com/Adam01405/obb-installer-2026-pro-max.git
cd obb-installer-2026-pro-max
gradle :app:assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`

Layout: `app/` (Compose UI + APK rewriter), `bootstrap/` (injected provider),
`keys/hub.keystore` (signing key), `.github/workflows/` (CI).

## 📄 Distribution

This app cannot be published on Google Play (Play forbids re-signing
third-party packages). It is distributed via GitHub Releases and (soon)
F-Droid.

## 🙏 Credits

- **Author & maintainer**: MT·xiaoyun ([@Adam01405](https://github.com/Adam01405))
- **Original project**: [`aciderix/APK-OBB-HELPER`](https://github.com/aciderix/APK-OBB-HELPER/)
- [`apksig`](https://android.googlesource.com/platform/tools/apksig/) by Google — APK signing library.
- The Android open-source community for documenting the binary AXML and ELF formats.

## 📄 License

MIT — see [LICENSE](LICENSE).
