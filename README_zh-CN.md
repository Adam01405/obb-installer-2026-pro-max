# 📦 OBB 安装器 2026 PRO MAX

### 免 Root、免电脑、免 Shizuku 安装 APK+OBB 安卓游戏 — OBB 藏在 APK 里，首次启动自动落位到正确目录。

[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Android 11+](https://img.shields.io/badge/Android-11%2B-3DDC84?logo=android&logoColor=white)](#兼容性)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack-Compose-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![No Root](https://img.shields.io/badge/Root-Not%20Required-brightgreen)](#)
[![No Shizuku](https://img.shields.io/badge/Shizuku-Not%20Required-brightgreen)](#)

---

<p align="center">
  <strong>🇨🇳 简体中文</strong> ·
  <a href="README.md">English</a>
</p>

---

## 🔄 二次开发声明

本仓库是 [`aciderix/APK-OBB-HELPER`](https://github.com/aciderix/APK-OBB-HELPER/)
的**二次开发项目**，保留了原始架构、设计理念与 MIT 许可。若重新分发本项目，请保留本声明。

---

## 🤔 这是什么？

**OBB 安装器** 是一款极小的安卓应用，一键安装以 **APK + OBB** 形式发布的游戏。
Android 11 以上，第三方应用无法再向 `Android/obb/<package>/` 目录写入文件，
本应用因此换了一条路：把 **OBB 内嵌进修补后的 APK**，由游戏自身在首次启动时解包
——游戏进程拥有自己目录的写权限，Android 允许它写入 OBB 目录。

> **一键操作。APK + OBB 放入，游戏找到数据并运行。**

## ✨ 功能特性

- 🪄 **一键安装** — 选择 APK 与 OBB，点 *安装*，完成。
- 📦 **无需 Root / 电脑 / Shizuku / 开发者模式**。
- 📐 **16KB 页对齐** — 自动提升已 16KB 对齐 ELF64 库的 `p_align`，让游戏在强制
  16KB 内存页的 Android 15+ 设备上也能运行。
- 🗂️ **双 OBB** — 同时打包 `main.*.obb` 与可选的 `patch.*.obb`。
- 🧩 **Split APK 支持** — 可同时选择基础 APK 与各 split APK，每个 split 都会用
  同一密钥修补、重签，并在同一会话中安装。
- 🛠️ **自动修复老游戏** — 自动提升 `targetSdkVersion`、修补旧版 `.so` 库
  （text relocations），让老游戏在现代化安卓上也能安装运行。
- 🌐 **三语界面** — 简体中文 / English / Français，跟随系统语言。
- 🔒 **完全离线** — 无遥测、无广告。
- 📦 **内置模式** — 把 `.apk` 和 `.obb` 放进 `app/src/main/assets/`，即可构建出
  针对特定游戏的一次性安装包。

## ⚡ 快速开始

1. 从 [Releases 页面](https://github.com/Adam01405/obb-installer-2026-pro-max/releases)
   下载 **`app-release.apk`**。
2. 允许安装未知来源应用（应用会引导你）。
3. 选择游戏的 APK（如有 split APK 一并选择），再选 `main.*.obb` 与可选的 `patch.*.obb`。
4. 点 **安装 APK + OBB** 并确认系统提示。
5. 启动游戏 — **首次启动解包 OBB**（约每 GB 30 秒），此后启动即为正常速度。

## 📱 兼容性

| 场景 | 状态 |
|---|---|
| 原生 / 厂商系统 Android 11–16 | ✅ 正常 |
| 单机 / 离线游戏 | ✅ 开箱即用 |
| 老游戏（target SDK ≤ 23、旧 `.so` 库） | ✅ 自动修补 |
| 代码内校验签名的游戏 | ⚠️ 拒绝运行（少见） |
| 联网竞技 / 服务型游戏 | ❌ 反作弊拒绝修补后的签名 |

## 🏗️ 工作原理

1. 修补二进制 manifest：提升 `targetSdkVersion`、注入 bootstrap `<provider>`。
2. 注入 bootstrap dex 与 OBB（以 STORED 方式存入 assets）。
3. 修补所有 `lib/**/*.so`：修复 text relocations、去除 RWX 段、已 16KB 对齐的
   提升 `p_align` 至 `0x4000`。
4. 用 `apksig` 重签（v1+v2+v3），通过 `PackageInstaller` 安装（base 与 splits 同一会话）。
5. 游戏首次启动时，注入的 provider 以游戏自身 UID 将 OBB 复制到
   `Android/obb/<package>/`。

## 🔧 从源码构建

```bash
git clone https://github.com/Adam01405/obb-installer-2026-pro-max.git
cd obb-installer-2026-pro-max
gradle :app:assembleRelease
```

产物：`app/build/outputs/apk/release/app-release.apk`

目录结构：`app/`（Compose UI 与 APK 重写器）、`bootstrap/`（注入的 provider）、
`keys/hub.keystore`（签名密钥）、`.github/workflows/`（CI）。

## 📄 分发

本应用无法上架 Google Play（Play 政策禁止重签第三方应用包）。通过 GitHub
Releases 分发，F-Droid 后续上线。

## 🙏 致谢

- **作者与维护者**：MT·xiaoyun（[@Adam01405](https://github.com/Adam01405)）
- **原始项目**：[`aciderix/APK-OBB-HELPER`](https://github.com/aciderix/APK-OBB-HELPER/)
- Google 的 [`apksig`](https://android.googlesource.com/platform/tools/apksig/) — APK 签名库。
- 记录二进制 AXML 与 ELF 格式的安卓开源社区。

## 📄 许可

MIT — 详见 [LICENSE](LICENSE)。
