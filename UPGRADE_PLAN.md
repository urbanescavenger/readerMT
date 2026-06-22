# 依赖升级计划

> 评估日期：2026-06-22
> 当前基线：minSdk 26 / targetSdk 36 / compileSdk 36 / AGP 9.2.0 / Gradle 9.4.1 / Kotlin 2.3.21 / KSP 2.3.6

最近一次提交（`7baf03c9f`）把 minSdk 提到 26（Android 8.0）。原先因“低 Android 版本缺 API”而锁版本的几条理由随 minSdk 26 已失效。当前编译已通过，没有强制必须升级的项；本文件作为后续系统（Android / 依赖）升级时的参考与待办清单。

版本对应 `gradle/libs.versions.toml`，最新版本查询自 Maven Central / Google Maven / JitPack（2026-06-22 快照）。

## 1. 锁版理由已失效 —— 可升（minSdk 26 后不再受 API 限制）

| 库 | 当前 | 目标 | 原锁版理由 |
|---|---|---|---|
| `commons-text` | 1.13.1 | 1.15.0 | 新版用 `Arrays.setAll`，Android 6 以下缺；minSdk 26 已满足 |
| `rhino` | 1.8.1 | 1.9.1 | 新版用 `VarHandle.compareAndExchange`，Android 8 以下无法编译；minSdk 恰好 26 |

升级时删除对应的 `noinspection NewerVersionAvailable` / `noinspection GradleDependency` 注释及上方说明注释。

> `rhino` 升级后建议在真机 API 26–32 上跑一次 JS 书源规则验证（`VarHandle` 在 Android 13 前原生支持情况需确认）。

## 2. 陈旧锁版（仅 `noinspection GradleDependency`、无 API 理由）—— 可升，需回归测试

| 库 | 当前 | 目标 | 备注 |
|---|---|---|---|
| `media3` | 1.8.0 | 1.10.1 | 视频 / TTS 回归；1.10 需 compileSdk 36（已满足） |
| `gsyvideoplayer` | 11.3.0 | 与 media3 联动一起升 | 视频播放回归 |
| `webkit` | 1.14.0 | 1.16.0 | web 渲染（详情页 / 视频简介）回归 |
| `activity` | 1.11.0 | 1.13.0 | |
| `lifecycle` | 2.9.4 | 2.11.0 | |
| `room` | 2.7.1 | 2.8.4 | 需 Kotlin 2.x + KSP（已满足）；数据库迁移测试 |
| `viewpager2` | 1.0.0 | 1.1.0 | |
| `firebaseBom` | 33.2.0 | 34.15.0 | 大版本跨越，确认 google-services 插件兼容 |
| `recyclerview` | 1.4.0 | — | 已是最新，锁版注释可删 |

## 3. 明确不要升级 —— 保持

| 库 | 当前 | 原因 |
|---|---|---|
| `jsoup` | 1.16.2 | #3811 破坏性变更（详见 jsoup/jsoup#2017），影响 `AnalyzeByJSoup.kt`、JsoupXpath 库 |
| `hutool` | 5.8.22 | 维护方刻意锁版 |
| `protobuf-javalite` | 4.26.1 | 维护方刻意锁版 |

## 4. 非锁版、可顺手小幅升级

| 库 | 当前 | 目标 |
|---|---|---|
| `coroutines` | 1.10.2 | 1.11.0 |
| `gson` | 2.13.2 | 2.14.0 |
| `okhttp` | 5.3.2 | 5.4.0 |
| `glide` | 5.0.5 | 5.0.7 |
| `soraEditor` | 0.24.4 | 0.24.6 |
| `core` | 1.17.0 | 1.19.0 |

## 前向兼容说明

历史上真正卡住依赖升级的是第 1 类两条（`Arrays.setAll` / `VarHandle`），随 minSdk 26 已解除。当前 targetSdk 36（Android 16）。

未来若再把 minSdk / targetSdk 提高（如 Android 13+ / API 33+）：
- 第 2 类的 `media3`、`firebaseBom` 等陈旧锁版需优先升级，避免新系统行为变更下旧库不兼容。
- `rhino` 1.9.x 的 `VarHandle` 在 API 33+ 原生可用，低于 33 需依赖 desugaring（`desugar_jdk_libs_nio`，仅 app 模块开启）——若 rhino 升级，确认 desugaring 覆盖 `VarHandle`。

升级任一项后均需全量回归：阅读界面、书源规则执行、视频 / TTS 播放、web 渲染、数据库迁移。
