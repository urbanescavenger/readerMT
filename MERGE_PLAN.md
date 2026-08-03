# 合并 reader-mt 到 readerMT:统一引擎单仓库计划

## Context(背景与动机)

两个项目都源自 gedoor/legado,但已分化成两条无法互通的分支:

- **reader-mt**(本地 `e:\GITHUB\reader-mt`,远端 urbanescavenger/reader-mt):**服务端**。Spring Boot 2.1.6 + Vert.x 3.8.1 + Kotlin 1.5.21 + Java 8 + Vue2 前端 + Docker + browserless 绕 CF。源自 hectorqin/reader(把 legado 引擎移植到服务端)。`io.legado.app.*` 实测 **100 文件**,已**基本剥离 Android**(`android.*`/`androidx.*` 的 active import 为 0——grep 命中皆为 `//` 注释行;Room `@Entity`/`@Parcelize` 亦注释掉,实体改纯 data class + Jackson;日志用 mu.KotlinLogging;持久化 `appDb` 全注释掉由 server 层接管;WebView 换成 browserless HTTP)。**注意**:服务端引擎**仍 active 导入 `com.script.*`**(`AnalyzeRule`/`AnalyzeUrl`/`BaseSource`/`AppConst` 4 文件引 `com.script.javascript.RhinoScriptEngine`/`com.script.SimpleBindings`,与 Android rhino-android 同族 API),并非已转 `org.mozilla.javascript` 直连——详见 §3.3。Rhino 通过 `src/lib/rhino-1.7.13-1.jar` 打包。**2021 年技术栈**。
- **readerMT**(本地 `e:\GITHUB\readerMT`,远端 urbanescavenger/readerMT,默认分支 `main`):**Android 原生**。fork 自 gedoor/legado(经 Luoyacheng/legado-E),7235 commits,Beta/Plus 双包名变体。`io.legado.app.*` 实测 **849 文件**,含完整 `ui/`/`service/`/`receiver/`/`data/dao`(21 个 Room DAO)/`lib/cronet`/`help/glide`。已多模块:`:app` + `:modules:book`(epub/umd) + `:modules:rhino`(gedoor rhino-android 包装,提供 `com.script.*` API)。**另有一个未登记的 `modules/web/`(包名 `legado-web`,Vite + Vue3 + TS 现代前端,不在 `settings.gradle` 内)**——本计划必须处理(见 §1)。**2026 年技术栈**:Kotlin 2.3.21、AGP 9.2.0、Gradle 9.4.1、KSP、Room 2.7.1、Rhino 1.8.1、OkHttp 5.3.2、jsoup 1.16.2、version catalog `gradle/libs.versions.toml`、Groovy Gradle。

**关键分化(已核实)**:两端同名 `io.legado.app` 文件**源码层不兼容**——
- `BookSource.kt`:Android 是 Room `@Entity` + `@Parcelize` + `androidx.room.*` + `android.text.TextUtils`;服务端是纯 data class + Jackson `@JsonIgnoreProperties`。
- `AnalyzeRule.kt`:Android import `com.script.rhino.RhinoScriptEngine`(rhino-android) + `BackstageWebView`(真 Android WebView) + `isMainThread`;服务端用 `org.mozilla.javascript.*`(打包 rhino 1.7) + `WebViewRenderHelp`(browserless HTTP)。
服务端的 100 文件是 Android 849 文件的一个**更旧、已剥离**的子集,既有平台分化也有**逻辑分化**。

**用户决策**:① 彻底统一引擎为平台无关模块,两端各自提供平台实现;② 合并进 readerMT 仓库(Android 优先,新增 `:server` 模块)。目标=**减少重复维护**(同一 bug/legado 上游改进只改一遍)。

**结论:可行,但是大工程**。预计 5–8 周(见各阶段),风险集中在 Android 数据层重构与服务端老栈升级。本计划分 5 个阶段,每阶段独立可发布、CI 可验证。本地无 Android SDK/Gradle,**云端 CI 是唯一编译反馈环,每次推送后必须监控**(遵守全局规则)。

---

## 1. 目标单仓库结构

目标 = readerMT `main`,**沿用其 Groovy Gradle + version catalog**(不转 KTS——readerMT 已是工作的 2026 配置,转换纯属额外风险与 churn;服务端模块也用 Groovy)。在现有 `:app`/`:modules:book`/`:modules:rhino` 基础上**新增** `:modules:legado-engine` 与 `:modules:server`。

```
readerMT/                          (monorepo root, 分支 main)
├─ settings.gradle                 (扩展现有,新增 include)
├─ build.gradle                    (扩展,加 server 相关插件声明)
├─ gradle/libs.versions.toml        (扩展,加 server 依赖)
├─ .github/workflows/
│  ├─ android-build.yml            (现有 readerMT Android CI,原名 android-build.yml)
│  ├─ docker.yml                   (从 reader-mt 移植)
│  └─ engine-cross-check.yml       (新增:书源一致性 parity 测试)
├─ modules/
│  ├─ legado-engine/               (新 :legado-engine, kotlin-jvm + java-library, Groovy build.gradle)
│  │  └─ src/main/kotlin/io/legado/app/{constant,exception,data/entities,help,help/http,model,utils,lib,platform}/...
│  ├─ rhino/  book/                 (现有,保留)
│  ├─ app/                          (现有 Android app :app,移入 modules/ 或保持 app/)
│  │  └─ src/main/java/io/legado/app/{ui,service,receiver,data/dao,data/entities(改 *Entity 包装),help/glide,help/gsyVideo,lib/cronet,lib/permission,lib/prefs,platform}/...
│  └─ server/                       (新 :server, 从 reader-mt 移植)
│     ├─ build.gradle               (spring-boot + vertx + javafx + application)
│     ├─ src/main/kotlin/com/htmake/reader/...      (server wrapper)
│     ├─ src/main/kotlin/com/htmake/reader/platform/... (server 平台实现)
│     ├─ src/main/resources/web/    (Vue dist,由 web/ 同步)
│     ├─ src/lib/                   (保留 rhino/xmlpull 打包 jar 直到期末)
│     ├─ Dockerfile*  docker-compose.yml
├─ web/                            (从 reader-mt/web 移植,Vue2 + Element UI;服务端管理 UI)
├─ modules/web/                    (现有,legado-web,Vite+Vue3+TS,当前未登记到 settings.gradle;Android 端配套前端)
└─ api.md, CHANGELOG.md, UPGRADE_PLAN.md, avd.bat/sh  (保留)
```

**两个前端的处置(必须明确,否则撞车)**:
- `modules/web/`(legado-web,Vite+Vue3+TS)= readerMT 已有的 Android 端 Web 前端;`web/`(reader-mt,Vue2+Element UI)= 服务端管理 UI。**两者用途不同,并存不冲突**,但需在 §1 明确命名空间:`modules/web/` 服务 Android 侧(如局域网书源管理),仓库根 `web/` 服务 `:server`。
- **决策**:Phase 0 不动 `modules/web/`(保持未登记或单独登记,视其是否已用);Phase 2 移植 reader-mt 的 `web/` 到仓库根。若后续两前端功能重叠,统一为 `modules/web/`(Vue3)并在 `:server` 复用——但该统一非本计划目标,留作后续工单。
- CI:reader-mt 工作流名确认为 `docker.yml`/`dockerhub.yml`(移植目标)。readerMT 现有 Android 工作流名为 **`android-build.yml`**(非 `android.yml`,后文 CI 引用据此)。

`settings.gradle` 追加:
```groovy
include ':modules:legado-engine'
include ':modules:server'
```

**源码搬运**:
- readerMT `app/src/main/java/io/legado/app/{model,help,data/entities,data/entities/rule,constant,exception,utils,help/http,help/coroutine,lib(非Android)}` → `:legado-engine`(剥离 Android 后)。`{ui,service,receiver,data/dao,lib/cronet,lib/permission,lib/prefs,help/glide,help/gsyVideo}` 留 `:app`。
- reader-mt `src/main/java/com/htmake/reader/**` → `:server`。reader-mt `web/` → 仓库根 `web/`。reader-mt `Dockerfile*`/`docker-compose.yml`/`src/lib/` → `:server`。
- reader-mt `src/main/java/io/legado/app/**`(实测 100 文件旧快照)→ **丢弃作引擎源码**,仅作 `:server` 平台实现的剥离模板/参考。

**包名**:引擎保留 `io.legado.app.*`。Android 平台实现放 `io.legado.app.platform.*`(同 `:app` 模块内,无冲突)。服务端平台实现放 `com.htmake.reader.platform.*`。**引擎禁止 import `android.*`/`androidx.*`/`com.script.*`/`com.htmake.*`;`:server` 禁止在 `io.legado.app` 下定义类**——用 CI grep 强制。

---

## 2. 引擎 source-of-truth 决策

**以 Android readerMT 引擎为基准,剥离 Android 依赖生成 `:legado-engine`**,而非以服务端 100 文件旧快照升级。

理由:
- **时效与完整性**:readerMT 跟随最新 legado(849 vs 100),含完整 RSS/localBook/analyzeRule/webBook/HttpHelper/CookieStore/CacheManager。服务端快照更旧且有**逻辑分化**(不仅是平台),升级旧快照=重新移植所有 fork 后的 legado 改动,等于重做 readerMT 已有的工作。
- **服务端已剥离的版本=剥离模板**:服务端每个文件展示了"如何剥离 Android",作为逐文件参考,而非源码。
- 代价:Phase 3(协调旧快照缺口)塌缩进 Phase 1;Phase 2 只剩写服务端平台实现。

诚实评估:从 849 文件剥离比从 100 文件升级"机械工作"更多(去 `@Entity`/`@Parcelize`/`com.script.*`/Room DAO/真 WebView),但可 grep 自动化、有界;而"逻辑协调"无界、风险高且易晚发现。故选前者。

---

## 3. 平台抽象 seam 设计

引擎暴露 SPI 包 `io.legado.app.platform.*`(接口 + holder)。`Platform` 全局 holder(lateinit,各平台启动时注入)。引擎源码**零** `android.*`/`com.htmake.*`/`com.script.*` import。

### 3.1 实体/DTO
- 引擎:`data/entities/*`(实测 39 文件,含 `rule/` 子目录——比初版估计的 29 多约 35%,Phase 1 工期据此上调)→ 纯 data class。去掉 `@Entity`/`@Parcelize`/`@TypeConverters`/`android.text.TextUtils`(换引擎内 `StringUtils`)/`androidx.room.*`。保留 Gson `@SerializedName`(两端共用 Gson)与必要 Jackson 注解(服务端 JSON 互操)。
- Android `:app`:同 29 名改 `*Entity`(`@Entity`/`@Parcelize`/`@TypeConverters`,字段与列名与现状**逐字段一致**以保 Room schema/迁移不变),加 `fun BookSource.toEntity()`/`fun BookSourceEntity.toModel()` 映射。21 个 `data/dao/*` **留在 `:app`**,返回 `*Entity`,映射只在 repository seam 做(不动 DAO SQL)。
- 服务端 `:server`:直接用引擎 DTO,经 Vert.x/JDBC repository 映射到行(无 Room)。
- 关键文件:readerMT `app/.../data/entities/`(共 39 文件,含 `BookSource`/`Book`/`BookChapter`/`BookGroup`/`Bookmark`/`ReplaceRule`/`RssSource`/`RssArticle`/`SearchBook`/`SearchKeyword`/`TxtTocRule`/`Cache`/`Cookie`/`BaseBook`/`BaseSource` 及 `rule/*`);参考 reader-mt `src/main/java/io/legado/app/data/entities/*` 已剥离版。

### 3.2 持久化
- 引擎 `io.legado.app.platform.repo` 定义接口:`BookSourceRepository`/`BookRepository`/`ChapterRepository`/`CookieRepository`/`CacheRepository`/`SearchBookRepository`/`ReplaceRuleRepository`/`RssSourceRepository`/`BookmarkRepository`/`BookGroupRepository`/`TxtTocRuleRepository`/`SearchKeywordRepository`,配 `NoOp*` 默认实现(返回空/抛 NotImplementedError,使引擎可独立编译单测)。
- 引擎内原 `appDb.*Dao.*` 调用(Android 活,服务端已注释)全改为 `Repositories.xxx.yyy(...)`。
- Android `:app`:`RoomRepositories` 适配器(DAO↔接口 + Entity↔DTO 转换)——**Phase 1 Android 侧主体**。
- 服务端 `:server`:`VertxRepositories`(JDBC/Vert.x SQL),复用 reader-mt 现有持久化重皮肤。
- 参考模板:reader-mt `help/CacheManager.kt`/`help/http/CookieStore.kt`/`model/webBook/BookContent.kt` 的 `appDb` 已注释处。

### 3.3 JS 引擎(最高风险 seam)
- **现状澄清(经核实)**:两端引擎**都 active 依赖 `com.script.*` API**,不是"服务端已直连 Rhino"。
  - Android readerMT `AnalyzeRule.kt` 引 `com.script.rhino.RhinoScriptEngine`/`com.script.buildScriptBindings`/`com.script.CompiledScript`(gedoor rhino-android 包装,提供 `com.script.*` API)。
  - 服务端 reader-mt `AnalyzeRule.kt`/`AnalyzeUrl.kt`/`BaseSource.kt`/`AppConst.kt` 引 `com.script.javascript.RhinoScriptEngine`/`com.script.SimpleBindings`——**同族 API**(reader-mt `build.gradle.kts` 里 `rhino-android` 依赖已注释,改挂 `src/lib/rhino-1.7.13-1.jar`,但代码仍按 `com.script.*` 表面调用;该 jar 实际提供 `com.script.*`+`org.mozilla.*` 双符号,否则无法编译)。
  - 含义:两端 JS API 表面**比初版设想更接近**(都走 `com.script.*`),`com.script.*` → 直连 `org.mozilla.javascript` 的迁移**两端都要做**,而非仅 Android。这降低了"两端互不兼容"的恐惧,但 §5c parity 测试范围要覆盖**两端各自的 `com.script.*` → 引擎 `Rhino` 迁移**,不能只测 Android 一侧。
- **目标**:统一为 `org.mozilla.javascript:rhino` 直连(两端共用 Maven 依赖),引擎内用 `io.legado.app.platform.js.Rhino` 包装 `Context`/`Script`/`Scriptable`,弃 `com.script.*`。
- `rhino-android`/reader-mt 打包 jar 多出的能力需在引擎内补齐:(a) 打包 Rhino → 换 Maven 直连(版本统一为 readerMT 的 Rhino 1.8.1,服务端从 1.7.13 升级,见 Phase 4);(b) AssetManager 资源解析 → 引擎 `ScriptAssetProvider` SPI(Android 读 assets,服务端读 classpath);(c) `buildScriptBindings`/`CompiledScript`/`getRuntimeScope` 助手 → 引擎内包装。
- 引擎文件:readerMT `app/.../model/analyzeRule/AnalyzeRule.kt`(Android)与 reader-mt `AnalyzeRule.kt`/`AnalyzeUrl.kt`/`BaseSource.kt`/`AppConst.kt`(服务端)的 `com.script.*` 调用**两套都要**改成 `io.legado.app.platform.js.Rhino`。grep 全 `import com.script` 站点(两端)一并改。
- **缓解**:Phase 1–2 保留 `RhinoAndroidEngine`(包现有 `com.script.*` 旧实现)作 `:app` 默认(后台 flag),`DirectRhinoEngine`(引擎内 `org.mozilla` 直连)与 `RhinoAndroidEngine` 双实现;仅当 §5c parity 测试在 50+ 真实书源**两端都**通过后才切默认。服务端同理保留旧 `com.script.*` 路径作 fallback 至 parity 绿。

### 3.4 WebView
- 引擎接口 `io.legado.app.platform.web.WebViewRenderer`:`startBrowserAwait(url,method,headers,body,charset,js)`/`getStrResponse(...)`/`evalJS(...)`。引擎 `AnalyzeUrl` 等改调 `Platform.webView.*`。
- Android `:app`:真 `BackstageWebView`(保留 readerMT `help/http/BackstageWebView.kt`/`AjaxWebView.kt`)。
- 服务端 `:server`:移植 reader-mt `help/WebViewRenderHelp.kt`(browserless HTTP),去掉其 `com.htmake.reader.config.AppConfig`/`SpringContextUtils` import,经 `AppConfigProvider` 注入 URL。

### 3.5 配置/偏好
- `AppConfigProvider`(`remoteWebViewApi`/`cachePath` 等)+ `Preferences`(键值,沿用 `constant/PreferKey.kt` 键)。
- Android:`SharedPreferencesAppConfigProvider` + 现有 `lib/prefs`。服务端:`SpringAppConfigProvider`(背靠 reader-mt `com.htmake.reader.config.AppConfig`)。

### 3.6 平台上下文
- `PlatformContext`:`cacheDir`/`filesDir`/`externalFilesDir?`。
- Android:由 `applicationContext`。服务端:由 Spring/Vert.x 配置,替换 reader-mt `com.htmake.reader.init.appCtx`。
- 引擎文件:reader-mt `utils/ACache.kt`、`help/JsExtensions.kt` 现引 `appCtx` → 改 `Platform.context.cacheDir`。

### 3.7 日志
- 引擎统一 `io.github.microutils:kotlin-logging` + `slf4j-api`。Android 加 `slf4j-android` binding(替 timber/logcat);服务端留 logback。reader-mt 引擎已用 mu.KotlinLogging,同法套到 Android 来源文件。

### 3.8 Android 专属 utils
- `android.text.TextUtils` → 引擎 `StringUtils`(reader-mt `utils/StringUtils.kt` 复用);`android.util.Log` → mu.KotlinLogging;`isMainThread` → `Platform.isMainThread()` SPI(Android `Looper`,服务端恒 false);`android.net.Uri` → `java.net.URI`/`okhttp3.HttpUrl`;`Context` 文件助手 → `PlatformContext`。
- grep 目标:`import android.text|android.util|android.os|android.net.Uri|android.content|androidx.annotation.Keep|androidx.room|com.script|kotlinx.android.parcelize`。

---

## 4. 分阶段执行

### Phase 0 — 准备(约 3–5 天)
- 在 readerMT 建 `monorepo/unify` 分支。
- `git subtree add --prefix=vendor/reader-mt https://github.com/urbanescavenger/reader-mt main`(或一次性拷贝,只要参考不构建)。
- 扩展 `settings.gradle`/`build.gradle`/`gradle/libs.versions.toml`,新增空 `:modules:legado-engine`(kotlin-jvm + java-library)与 `:modules:server`(占位),均能空编译。**保持 Groovy**。
- 移植 reader-mt `.github/workflows/docker.yml`/`dockerhub.yml` → `.github/workflows/`;加 `engine-cross-check.yml` 骨架。
- **验证(云端 CI)**:`:app` APK 构建仍绿;两空模块构建绿。推送后监控。

### Phase 1 — 从 Android 上游抽取 `:legado-engine`(约 2–3 周)
- 搬运 + 剥离(grep 驱动):去 `@Entity`/`@Dao`/`@Database`/`@TypeConverters`/`@Parcelize`/`androidx.room.*`/`android.text.*`/`android.util.*`/`android.os.*`/`android.content.*`/`android.net.Uri`/`com.script.*`/`kotlinx.android.parcelize`/`androidx.annotation.*`。
- `appDb.*` → `Repositories.*`;`com.script.*` → `Rhino`(先用 `DirectRhinoEngine`,`RhinoAndroidEngine` 作 `:app` fallback);`BackstageWebView`/`AjaxWebView` → `Platform.webView.*`;`appCtx` → `Platform.context.*`。
- 定义全部 SPI(`Platform`/`Repositories`/`WebViewRenderer`/`AppConfigProvider`/`RhinoEngine`/`ScriptAssetProvider`/`PlatformContext`)。
- `:app` 平台实现(Phase 1a,可滞后):`RoomRepositories`、`AndroidWebViewRenderer`、`SharedPreferencesAppConfigProvider`、`AndroidPlatformContext`、`RhinoAndroidEngine` fallback;`*Entity` 包装 + 映射;DAO 不变。
- **验证(云端 CI)**:`:app` APK 构建并启动,书架渲染;`:legado-engine` 用 NoOp 实现独立构建。**风险**:破坏 7235-commit app 的 Room 数据层。**缓解**:`*Entity` 逐字段同现状(同 `@ColumnInfo`/converter)保 schema 与迁移;映射只在 repository 边界;为 BookSource/Book/BookChapter/Cache/Cookie 五个最热 DAO 加 instrumented 测试;Phase 1 带 feature flag 可回滚到 app 内引擎副本。

### Phase 2 — 移植 `:server`(约 1–2 周)
- 拷 reader-mt `com/htmake/reader/**` → `:server`;拷 `Dockerfile*`/`docker-compose.yml`/`src/lib/`/`web/`/`src/main/resources/web`(构建期从 `web/dist` 同步)。
- `:server/build.gradle` 基于 reader-mt `build.gradle.kts`(Groovy 化)。**本阶段锁版本不升级**(Spring 2.1.6/Vert.x 3.8.1/Kotlin 1.5.21 维持),避免引入新变量——升级放 Phase 4 单独 PR。
- 删除 reader-mt 旧 `io/legado/app/**` 快照;`:server` 改消费 Phase 1 的 `:legado-engine`。
- 重指服务端反向耦合:`appCtx` → `:server` 的 `ServerPlatformContext`;`WebViewRenderHelp` 改 `:server` 的 `WebViewRenderer` 实现(去 `AppConfig`/`SpringContextUtils` import);`CbzFile.kt` 的 `com.htmake.reader.utils.{getFileExtetion,xml2map}` → 移入引擎 `XmlUtils`/`StringUtils`(纯 JVM)。
- 服务端平台实现:`VertxRepositories`、`BrowserlessWebViewRenderer`、`SpringAppConfigProvider`、`ServerPlatformContext`、`DirectRhinoEngine`(引擎内)。
- **验证(云端 CI)**:Docker 镜像构建并启动;`curl /` 返回 Vue index;`web/dist`→`resources/web` 同步生效。**风险**:旧 Spring/Vert.x 在新 Gradle/Kotlin 下解析——锁版本缓解。

### Phase 3 — 协调引擎到最新上游 + 统一 seam(约 1–2 周,最高风险)
- 因起点是 readerMT(已最新),此阶段较小:cherry-pick 引擎抽取后落地的 legado 改进;若 Phase 1 耗时长则对最新 `main` 重抽。
- **逐文件 diff** reader-mt 100 文件 vs 新引擎,把服务端独有的 bugfix/行为补丁移植进引擎(两端共享)。重点:`AnalyzeRule`/`AnalyzeUrl`/`JsExtensions`。
- `:app` Rhino 默认 `RhinoAndroidEngine` → `DirectRhinoEngine`(parity 通过后)。
- 统一 `JsExtensions`(两端均改走 `Platform`,恢复服务端快照丢弃但 JVM 无害的 Android 扩展)。
- **验证**:`android-build.yml`+`docker.yml` 双绿;`engine-cross-check.yml` 跑 parity。**风险**:旧服务端逻辑 vs 新引擎逻辑的解析回归。**缓解**:Phase 3 期间 `:server` 可仍消费旧快照 fallback,parity 绿后才切 `:legado-engine`。

### Phase 4 — 硬化(约 1 周)
- 端到端验证(§5)。
- **版本升级单独 PR**:Spring 2.1.6→2.7.x/3.x(javax→jakarta,Java 8→17)、Vert.x 3.8→4.x/5.x、Kotlin 1.5→2.3、Rhino 1.7→1.8.1、OkHttp 4→5、jsoup 1.14→1.16(注意 #3811 breaking)、Gradle 升级。**每次升级独立 PR + CI 重跑,不打包**。
- parity 持续绿且无 diff 后删除 `vendor/reader-mt` 参考子树。
- `DirectRhinoEngine` parity 连续两个发布周期稳定后删除 `RhinoAndroidEngine` fallback。

---

## 5. 验证(端到端)

本地无 Android SDK/Gradle,**云端 CI 是唯一编译环,每次推送后监控**。

a. **Android APK 构建并启动**(`android-build.yml`):`:app:assembleBetaRelease`(及 Plus);加 emulator 步骤:装 APK、启动 `MainActivity`、断言书架渲染至少一本书(用 checked-in 测试书架 DB 种子);冒烟:用 checked-in BookSource 拉一章断言非空。

b. **服务端 Docker 构建并服务 UI**(`docker.yml`):构建 `:server/Dockerfile`(+`.slim`);`docker-compose up` 带 browserless-chromium sidecar;CI `curl http://localhost:8080/` 返回 Vue `index.html`,`curl /getBookshelf` 返回 JSON;冒烟:POST BookSource JSON 跑 `/searchBook` 断言有结果。

c. **共享书源 parity 测试**(`engine-cross-check.yml`)——**统一规则兼容性闸门**:
- `:legado-engine/src/test/resources/sources/*.json` 约 50 个代表性书源(xpath/jsonpath/regex/js 混合,含 JS 重源以压 Rhino)。
- `:legado-engine` 纯 JVM 测试 `BookSourceParityTest`:每源对固定 HTML/JSON fixture 跑 search/toc/content 解析,产出规范化结果 hash。
- 同测试在 Android(`:app` androidTest,先后用 `RhinoAndroidEngine` 与 `DirectRhinoEngine`)与服务端(`:server` test,`DirectRhinoEngine` + mock `BrowserlessWebViewRenderer`)各跑一遍。
- **通过判据**:三端对所有 50 源的 search/toc/content 规范化 hash 完全一致,任何分歧阻塞合并。

d. **CI 强制(常开)**:`:legado-engine` 禁 import `android.*`/`androidx.*`/`com.script.*`/`com.htmake.*`(构建失败);`:server` 禁在 `io.legado.app` 下定义类;`android-build.yml`/`docker.yml` 设为 `monorepo/unify` 合并 `main` 的必需状态检查。

---

## 6. 风险与缓解(按严重度)

1. **(致命)破坏 live Android app 的 Room 数据层**。剥离 `@Entity`/DAO 成引擎 DTO + `*Entity` 包装有 SQLite 迁移/`@TypeConverters` 静默破坏风险。**缓解**:`*Entity` 逐字段同现状;映射只在 repository 边界;热门表加迁移测试;Phase 1 带 flag 可回滚。
2. **(高)rhino-android → 直连 Rhino 的行为差异**。书源可能依赖 rhino-android 的 scope 绑定/资源解析。**缓解**:Phase 1–2 留 `RhinoAndroidEngine` 默认;50+ 真源 parity 通过后才切。
3. **(高)服务端旧快照逻辑分化**。现能跑的服务端书源对新引擎可能回归。**缓解**:Phase 3 逐文件 diff 移植服务端独有补丁;`:server` 保留旧快照 fallback 至 parity 绿。
4. **(中)`io.legado.app` 包名冲突**。**缓解**:CI grep + Detekt 强制引擎/服务端 import 边界。
5. **(中)旧服务端栈(Spring 2.1.6/Vert.x 3.8.1/Kotlin 1.5.21)在新 Gradle/Kotlin 下解析**。**缓解**:Phase 2 锁版本;升级仅 Phase 4 单独 PR。
6. **(中)WebView seam 分化(真 WebView vs browserless)**。部分源 JS 只有真浏览器能跑。**缓解**:两者都走 `WebViewRenderer` SPI,引擎 `AnalyzeUrl` 平台无关;需真 WebView 的源标注为 Android-only。
7. **(低)Groovy build 配置扩展**。新增模块的 build.gradle 语法错误。**缓解**:Phase 0 空 CI 闸门。
8. **(低)`web/dist`→`server/resources/web` 同步**。**缓解**:在 `:server/build.gradle` 保留 reader-mt 的同步 Gradle task。

---

## 7. 关键文件

**服务端(剥离模板/平台实现源)**
- `e:\GITHUB\reader-mt\src\main\java\io\legado\app\help\JsExtensions.kt`(引 `appCtx`→`Platform.context` 模板)
- `e:\GITHUB\reader-mt\src\main\java\io\legado\app\help\WebViewRenderHelp.kt`(browserless→`WebViewRenderer` SPI + `AppConfigProvider` 模板)
- `e:\GITHUB\reader-mt\src\main\java\io\legado\app\data\entities\BookSource.kt`(已剥离纯 data class→引擎 DTO 模板)
- `e:\GITHUB\reader-mt\src\main\java\com\htmake\reader\config\BookConfig.kt` 与 `init/appCtx.kt`(服务端配置/上下文→SPI 实现源)
- `e:\GITHUB\reader-mt\build.gradle.kts`(服务端依赖→`:server/build.gradle` 基础)
- `e:\GITHUB\reader-mt\docker-compose.yml`(browserless sidecar)

**Android(引擎源 + 改造对象)**
- `readerMT:app/src/main/java/io/legado/app/model/analyzeRule/AnalyzeRule.kt`(`com.script.*` + `BackstageWebView` 耦合→`RhinoEngine`+`WebViewRenderer` SPI 的中心文件)
- `readerMT:app/src/main/java/io/legado/app/data/entities/BookSource.kt` 及 39 实体(含 `rule/*`,`@Entity`/`@Parcelize`→引擎 DTO + `*Entity` 包装)
- `readerMT:app/src/main/java/io/legado/app/data/dao/*`(21 DAO,留 `:app`,返回 `*Entity`)
- `readerMT:settings.gradle`、`gradle/libs.versions.toml`、`UPGRADE_PLAN.md`(多模块基线与版本锁版依据)

---

## 8. 总体可行性判断

**可行,且是减少重复维护的正确方向,但是 5–8 周的大工程**,不是一个 PR 能完成的。核心难点不是"搬代码"而是:
1. Android 849 文件引擎的 Android 依赖剥离 + Room 数据层重构(Phase 1,2–3 周,致命风险;实体 39 文件,比初估多约 35%);
2. 服务端 2021 老栈在引擎(2026 新栈)下的升级适配(Phase 2/4, javax→jakarta + Vert.x 3→4/5,高风险);
3. 两端已分化的引擎逻辑的 parity 校验(Phase 3 + parity 测试,决定"是否真正统一"的闸门)。

建议执行顺序严格按 Phase 0→4,每阶段云端 CI 绿(且 parity 测试绿)才进下一阶段;Phase 1 与 Phase 4 的版本升级务必拆成多个小 PR,绝不堆积。若资源/时间受限,可先只做 Phase 0–2(单仓库 + 两端各保留引擎副本 + parity 测试作监控),拿到"同仓 + 分歧可见"的收益,再视情况推进 Phase 3 彻底统一。

---

## 9. 执行进度与发现(2026-08-03,持续更新)

> 本地**无 Android SDK/Gradle,不能本地编译**;云端 CI 是唯一编译反馈环,每次推送后挂 Monitor 监控至终态。分支 `monorepo/unify`(已推 origin),所有已落地项 **CI 双绿**(`android-build.yml` + `engine-cross-check.yml`;`docker.yml` 见下)。

### 9.1 已落地并 CI 绿

**Phase 0**:模块脚手架。`:modules:legado-engine`(kotlin-jvm,Groovy)+ `:modules:server`(详见 9.3);`settings.gradle`/根 `build.gradle` 扩展;`vendor/reader-mt` 参考源(Phase 2 后已移为 `modules/server`,vendor/ 删除);`.github/workflows/{engine-cross-check.yml,docker.yml,dockerhub.yml}` + `android-build.yml` 加 `monorepo/unify` 触发。

**Phase 1(干净/机械/自包含部分,app/ 原件未动)**:源码**复制**进引擎,app/ 不变 → 两端同名类不在同一 classpath,无冲突;switchover(Phase 1c)时才让 app/ 依赖引擎 + 删 app/ 副本 + `*Entity` 改名。
- `platform/*` SPI:Platform(holder:context/webView/appConfig/scriptAssets/rhino/repositories+isMainThread)、PlatformContext、web/WebViewRenderer、AppConfigProvider(+userAgent)、ScriptAssetProvider、js/RhinoEngine(+ScriptBindings/CompiledScript,**已扩建模共享作用域/原型**)、repo/Repositories + CookieRepository/CacheRepository(+NoOp)。
- `exception/*`(8)、`constant/{BookSourceType,BookType,SourceType,AppPattern}`(@IntDef 剥)、`data/entities/rule/{BookListRule,BookInfoRule,SearchRule,TocRule,ContentRule,ExploreRule,ReviewRule}`(@Parcelize/Parcelable 剥)、`data/entities/{Cache,Cookie}`(@Entity 剥成纯 DTO,首个 Room→DTO 样板)。
- `utils/{GsonExtensions(+libs.gson),StringUtils(TextUtils 替:isEmpty/join),Utf8BomUtils,StringExtensions.splitNotBlank,NetworkUtils(纯子集 getBaseUrl/isIPAddress/getSubDomain,okhttp publicsuffix),LogUtils(printOnDebug 纯),EncoderUtils(java.util.Base64 + android flag 值映射,替 android.util.Base64)}`;`constant/AppLog`(stderr 占位,待 §3.7 接 kotlin-logging)。
- `help/http/{StrResponse(剥@Keep),RequestMethod,OkHttpExceptionInterceptor,DecompressInterceptor(+libs.okhttp),api/CookieManagerInterface,CookieUtils,SSLHelper(JVM trust-all 三件套,替 android),OkhttpUncaughtExceptionHandler,HttpHelper(引擎基础 okHttpClient:超时+trust-all SSL+2 拦截器+Keep-Alive+UA(Platform.appConfig),弃 Glide/SSLHelper-android/CookieManager/addressCache/Cronet,留 app 增强)}`;`help/{LruCache(JVM LinkedHashMap access-order,替 androidx.collection.LruCache),CacheManager(字符串缓存→Repositories.cache,弃 ACache 磁盘/@JavascriptInterface;AppCacheManager/WebCacheManager 留 app/),CookieStore(弃 android.webkit.CookieManager、appDb→Repositories.cookie、内存 map 替 CacheManager、getSubDomain 抽纯、折叠 helper)}`。
- `model/analyzeRule/{AnalyzeByJSoup,AnalyzeByRegex,AnalyzeByXPath(TextUtils→StringUtils),AnalyzeByJSonPath(剥@Keep),RuleAnalyzer,RuleData,RuleDataInterface}`(+libs.jsoup/jsoupxpath/json.path)。
- `help/JsExtensions` 簇①a/①b(strToBytes/bytesToStr、hex、timeFormatUTC、encodeURI、base64 全套、timeFormat)——✅ **9.4 结构返工已落地**:由 `object` 改为 `interface JsExtensions : JsEncodeUtils`(13 方法成默认方法)。
- `help/JsEncodeUtils` 加密簇——✅ **§9.5 A big-bang batch 1 已落地并 CI 双绿**(commit `6d15a43a0`):填入全部加密默认方法(md5/AES/DES/3DES/digest/HMac/createSymmetricCrypto/createAsymmetricCrypto/createSign),去 `@JavascriptInterface`,`android.util.Base64`→`EncoderUtils`(NO_WRAP flag 映射)。配套移植 `utils/MD5Utils`(纯 JVM)、`utils/StringExtensions.isHex`、`help/crypto/{SymmetricCryptoAndroid,AsymmetricCrypto,Sign}`(去 `@Keep`,背靠 hutool-crypto)。`build.gradle` 加 `libs.hutool.crypto`(catalog 已有 5.8.22)。自包含,不碰 AnalyzeUrl/BaseSource/com.script。
- **batch 2a(互递归核心第 1 子批,增量叶子)已落地并 CI 双绿**(commit `4234d13f7` + 修 `de898f705`):补引擎 util/HTTP 缺口 + 解环,纯增量无 flip。`build.gradle` 加 `kotlinx-coroutines-core`+`commons-text`(catalog 已有)。`utils/NetworkUtils`(+getAbsoluteURL×2/encodedQuery/encodedForm/getDomain)、`utils/StringExtensions`(+isAbsUrl/isDataUrl/isJson/isJsonObject/isJsonArray/isXml/parseIpsFromString)、`utils/{ThrowableExtensions,MapExtensions,EncodingDetect}`(新;EncodingDetect 的 icu4j 统计检测器暂缓,那批 Java 含 android.os/android.system 耦合,无 meta 时回退 UTF-8,parity 留 §5c)、`help/http/{OkHttpUtils(扩展集),CookieManager(cookieJarHeader+mergeCookies)}`、`help/http/HttpHelper`(+getProxyClient)。**解环**:`ConcurrentRecord` 从 `AnalyzeUrl` 内嵌抽成顶层 `help/ConcurrentRecord.kt`;`help/ConcurrentRateLimiter.kt` 构造改取 `(key,concurrentRate)` 原始参(不再依赖 BaseSource)——§9.4 互递归环(AnalyzeUrl↔ConcurrentRateLimiter↔ConcurrentRecord)已断。CI 修过 1 处:`StringUtils.isEmpty` 是对象方法非顶层函数。

**两个 Android 耦合 helper 已架构重构进引擎并绿**:CookieStore(弃 android.webkit.CookieManager)、CacheManager(JVM LruCache 弃 androidx/ACache)。证明盲推架构重构可行(至今仅 1 处 KDoc 嵌套 `/*` 注释语法修复 + 1 处缺 import + 1 处边界 grep 锚定修复)。

### 9.2 服务器(Phase 2)已验证可跑(成品验证)

- reader-mt 服务器搬进 `modules/server`(`vendor/reader-mt` 改名而来)。**保留自有工具链**:Docker 内 `gradle:7-jdk8` + `cli.gradle`(Kotlin 1.5.21/Java 8)构建,运行时 `amazoncorretto:8-jre`。详见 9.3 为什么不能作为 root-Gradle 子项目。
- `settings.gradle` **移除 `:modules:server`**(root Gradle 不构建它);`engine-cross-check.yml` 只构建 `:legado-engine`(移除 `:server` 构建与 server 包名边界 grep——服务器仍带 io/legado/app 快照,该边界是 Phase 3 目标)。
- `docker.yml`:`workflow_dispatch` + tag `v*` 触发;构建 `modules/server/Dockerfile.source` 推 GHCR(`ghcr.io/urbanescavenger/readermt:unify` 及 `:sha-<short>`,分支推送也推 `:unify` 后改为 dispatch+tag 节省 CI)。镜像默认 private,需 GitHub Packages 设 public 才能匿名 pull。
- **CI 已验**:Docker 镜像构建成功,`docker run` 后 `curl localhost:8080/` 6 秒返回 Vue 首页。
- **用户端验(完整)**:用样例书源「未来天王」(`http://www.weilaitianwang.info#🎃`,纯 HTTP、不过 CF)经 UI 导入后**可搜书、可阅读**。此书源为已知可用黄金样例,将来 §5c fixture parity 用(届时需抓其 search/书详情/章节页 HTML 作 fixture)。

### 9.3 关键发现 1:服务器必须保留自有工具链(修正 §4 Phase 2「锁版本」假设)

monorepo 是 **Kotlin 2.3.21 / Java 17 / Gradle 9.4.1**;reader-mt 服务器是 **Kotlin 1.5.21 / Java 8 / Spring 2.1.6 / Vert.x 3.8.1**。**一个 Gradle 构建无法容纳两个 Kotlin 版本**(Kotlin 1.5 编译器/运行时读不了 2.3 元数据,Java 8 运行时加载不了 Java 17 字节码)。所以 §4「Phase 2 锁版本不升级」的假设在「服务器作为 root-Gradle 子项目消费 `:legado-engine`」这件事上**不成立**。

**结论**:服务器**保留自有工具链,只在 Docker 内构建,不进 root Gradle**(已落地)。代价:服务器要消费 `:legado-engine` 的 jar,必须**先升级到 Kotlin 2.3.21 + 兼容 Java + 兼容 Spring/Vert.x**(即 §4 的 Phase 4 升级,被现实提前成「服务器侧 parity」的门槛)。两条 parity 路(服务器活站 diff、§5c fixture)都绕不开硬核(见 9.4)。

### 9.4 关键发现 2:真实引擎层级结构与 big bang 真实规模(修正 §3.3 / §8 估计)

执行中摸清真实结构:`interface JsEncodeUtils`(~500 行加密接口)← `interface JsExtensions : JsEncodeUtils` ← `interface BaseSource : JsExtensions` ← `BookSource`/`RssSource`(data class)。**注意:`JsExtensions`/`JsEncodeUtils` 是接口,不是 object**——9.1 中 `help/JsExtensions` 簇①a/①b 写成了 `object`,**结构错误,需返工**为 `interface JsEncodeUtils` + `interface JsExtensions : JsEncodeUtils`。

**⚠️ 措辞修正**:本节原文将 JsEncodeUtils 称为"编码默认方法",但核实 app 后确认 `JsEncodeUtils` 是**纯加密**(md5/AES/DES/3DES/digest/HMac/createSymmetricCrypto/createAsymmetricCrypto/createSign,517 行);9.1 已搬的 13 个方法(strToBytes/bytesToStr/hex/base64/encodeURI/timeFormat/timeFormatUTC)在 app 中**全部属 `JsExtensions`**,不属 `JsEncodeUtils`。返工须忠实 app 拆分。

**✅ 9.4 结构返工已落地并 CI 双绿**(commit `ea05bb90e`,`monorepo/unify`):新建空壳 `interface JsEncodeUtils`(加密簇待 big-bang);`help/JsExtensions` 由 `object` 改为 `interface JsExtensions : JsEncodeUtils`,13 个已搬方法成默认方法。**暂不加**抽象 `getSource()/getTag()`(避免拉入未搬的 BaseSource)。纯结构、零新依赖/新 import,边界 grep 仍 0 命中,app/ 未动。继承骨架就位,等用户在 9.5 定 A/B。

真实 big bang ≈ **5000+ 行互递归一次性进引擎**:
- `JsEncodeUtils`(~500 行加密:md5/AES/DES/3DES/HMac/对称/非对称/签名,用 hutool-crypto + `help.crypto.{AsymmetricCrypto,Sign,SymmetricCryptoAndroid}` android 加密需移植;书源解密正文依赖,**引擎必需**,parity 敏感)。
- `JsExtensions` 剩余 ~85 方法(HTTP:ajax/connect 调 `AnalyzeUrl`;get/post/head 调 jsoup+SSLHelper+`getSource():BaseSource`+ConcurrentRateLimiter;WebView:webView*/startBrowser→Platform.webView;Cookie:getCookie→CookieStore;文件→Platform.context;源:getSource/getTag)。
- `BaseSource`(interface:login 流程[RowUi UI + `AppConst.androidId` AES + SymmetricCryptoAndroid,app 交互专属,拟省略进 app] + variable[CacheManager] + evalJS[com.script→Platform.rhino + getShareScope] + ConcurrentRateLimiter)。
- `ConcurrentRateLimiter` ↔ `AnalyzeUrl.ConcurrentRecord`(互递归,需把 ConcurrentRecord 抽独立类解环)。
- `AnalyzeUrl`(~700行)+ `AnalyzeRule`(~700行):com.script→RhinoEngine、WebView→Platform.webView、JS 共享作用域语义(`getShareScope`/`SharedJsScope`→`Platform.rhino.getOrCreateSharedScope`,§3.3 最高风险 seam,只能 parity 验)。
- `help.source`(getShareScope/clearExploreKindsCache,重 android)、`SharedJsScope`。

这是计划 **Phase 1 的整个 2–3 周最高风险核心**,非一条盲推能收敛;且 `JsExtensions` 增量搬在 HTTP 簇就被 `AnalyzeUrl`/`BaseSource`/com.script 钉死(互递归),不能逐簇,必须 big bang。

### 9.5 下一步抉择(用户已选 A;batch 1 + 2a + 2b + 2c-1/2c-2/2c-3 已绿,2c-4 增量推进中)

- **A**(进行中):盲推核心。**修正 §9.4 规模估计**——三份 Explore 报告(AnalyzeUrl/AnalyzeRule/BaseSource)核实:真正互递归**只有一个环**(AnalyzeUrl↔ConcurrentRateLimiter↔ConcurrentRecord),抽 `ConcurrentRecord` 为顶层类即断(batch 2a 已断);其余是 import/SPI 改写 + 补 util 缺口,**可分小批增量、各自 CI 绿**,只有 flip 步骤需闭包同批。
  - ✅ batch 1 = `JsEncodeUtils` 加密簇(`6d15a43a0`,自包含)。
  - ✅ batch 2a = 增量叶子(utils/HTTP helpers/解环/依赖,`4234d13f7`+`de898f705`)。
  - ✅ batch 2b = SPI 扩展 + BaseSource interface(`da15431a8`):`RhinoEngine` 加 `removeSharedScope`/`eval(+coroutineContext)`/`CompiledScript.eval(+ctx)`、`getOrCreateSharedScope` 改返 `Any?`;`WebViewRenderer` 加 `renderHtmlWithJs`;`AppConst.UA_NAME`;`data/entities/BaseSource` engine interface(可移植主体 + app-only open fun: getLoginInfo/putLoginInfo/getLoginInfoMap/refreshExplore);`JsExtensions` 补抽象 `getSource():BaseSource?`/`getTag():String?`(9.4 deferred,现补;同模块循环引用 help↔data.entities Kotlin 允许)。CI 双绿。
  - ✅ batch 2c-1 = 实体最小 interface + SPI 扩展(`e8de3e3e6`,CI 绿):引擎 `interface BaseBook:RuleDataInterface`(name/author/bookUrl)、`BookChapter:RuleDataInterface`(title)、`BookSource:BaseSource`、`BaseRssArticle:RuleDataInterface`/`RssArticle:BaseRssArticle`(标记接口,AnalyzeRule 的 `as? BookSource`/`as? RssArticle` 守卫用;Phase 1c switchover 时 app 实体类 implement,deferred)。SPI:`WebViewRenderer.renderHtmlWithJs` 加 `sourceRegex/overrideUrlRegex/delayTime` 11 参重载;`RhinoEngine.getOrCreateSharedScope(srcKey,initJs,coroutineContext)` 重载(init 下载+编译需 cancellation);`PlatformContext.externalCache`;`AppConfigProvider.threadCount`;`platform/webbook/WebBookProvider`+NoOp+`Platform.webBook`(AnalyzeRule.reGetBook/refreshTocUrl→app WebBook,服务端 NoOp 抛 UnsupportedOperationException)。
  - ✅ batch 2c-2 = 叶子工具 + 独立 JsExtensions 方法(`807264ded`+修 `e647b3aa9`,CI 绿):移植 `ChineseUtils`(quick-transfer-core,catalog 已有 0.2.17)、`JsURL`、`StringUtils.stringToInt`(+fullToHalf/chineseNumToInt/chnMap)、`HtmlFormatter`(**解耦**:内联 `AnalyzeUrl.paramPattern` 正则,去 AnalyzeUrl import,使 htmlFormat 可独立 flip)、`FileUtils`(delete/getPath/getCachePath/createFolderIfNotExist,`Platform.context.externalCache`)、`FileExtensions`(createFileReplace/createFolderIfNotExist);`RhinoEngine.currentCoroutineContext()`(rhinoContextOrNull 等价,ThreadLocal 由 impl 设,JsExtensions 读做 cancellation/runBlocking);`build.gradle` 加 `libs.quick.chinese.transfer.core`。JsExtensions 簇②默认方法:getCookie×2/randomUUID/getWebViewUA(Platform.appConfig.userAgent)/t2s/s2t/htmlFormat/toNumChapter/toURL×2/log/logType/getFile/readFile/readTxtFile×2/deleteFile + `private val context`(CoroutineContext)。CI 修过 1 处:KDoc 内 `webView*/` 的 `*/` 被当注释结束符(经典嵌套陷阱)→ 改 `webView 系列(...)`。
  - ✅ batch 2c-3 = flip 闭包(`dc89267f2`+修 `026bf72a6`,CI 双绿):**闭包规模大幅缩小**——三份 Explore 报告核实真正互递归**只有** `AnalyzeRule.ajax → AnalyzeUrl`(AnalyzeRule/AnalyzeUrl `: JsExtensions`,ajax 默认调 AnalyzeUrl);其余 JsExtensions(ajaxAll/connect/get/post/head/webView*/downloadFile/archives/queryTTF)**不在互递归**,可闭包后增量加。故最小闭包 = `AnalyzeUrl`(978行)+ `AnalyzeRule`(973行)+ JsExtensions `ajax`×2 + rhino 依赖。剥离:android.util.Base64→EncoderUtils、@Keep/@SuppressLint 去掉、com.script→Platform.rhino(newBindings/getRuntimeScope/eval(+ctx)/compile/getOrCreateSharedScope(+ctx)/CompiledScript.eval(+ctx))、BackstageWebView→Platform.webView.renderHtmlWithJs、getShareScope→Platform.rhino.getOrCreateSharedScope(getKey,jsLib,ctx)、AppConfig→Platform.appConfig(Cronet 分支删)、Debug.log→log、Book→BaseBook、ConcurrentRateLimiter(source)→(key,concurrentRate);删 getMediaItem/getGlideUrl(app-only)+嵌套 ConcurrentRecord(已顶层);AnalyzeRule 保留 `org.mozilla.javascript.NativeObject`/`Scriptable`(引擎直连 rhino,§3.3)+topScopeRef/evalJSCallCount 共享作用域 L2 缓存(原型链语义);`build.gradle` 加 `libs.mozilla.rhino`。CI 修过 1 处:`EncoderUtils.escape`(charset=="escape" 时 AnalyzeUrl 用,纯 JVM,补到引擎 EncoderUtils)。**JS 共享作用域语义**(`getShareScope`/`SharedJsScope`→`Platform.rhino.getOrCreateSharedScope`)语义盲定,§5c parity 兜底。
  - ⏭️ batch 2c-4 = 剩余 JsExtensions(闭包后增量,各自 CI 绿):
    - ✅ 2c-4a = WebView 簇 + HTTP 簇(`8da3253c2`+修 `019ccbbfc`,CI 绿):webView×2/webViewGetSource×3/webViewGetOverrideUrl×3(→`Platform.webView.renderHtmlWithJs` 8/11 参;`isMainThread`→`Platform.isMainThread()`);ajaxAll/ajaxTestAll(AnalyzeUrl.getStrResponseAwait + `mapAsync` 并发,移植 `utils/FlowExtensions.mapAsync` Semaphore-based;`Platform.appConfig.threadCount`);connect×3(AnalyzeUrl.getStrResponse);get/post/head×2(jsoup `Connection`+`SSLHelper.unsafeSSLSocketFactory`+`ConcurrentRateLimiter(key,concurrentRate).withLimitBlocking`+`cookieJarHeader`)。CI 修 1 处:`mapAsync` 的 `buffer`/`channelFlow` 在 `kotlinx.coroutines.flow` 子包(非 `kotlinx.coroutines`),`await` 是 `Deferred` 成员非顶层 import。
    - ✅ 2c-4b = 文件/网络簇(`eb2500833`,CI 绿):importScript(http→cacheFile/else readTxtFile)、cacheFile×2(`md5Encode16`+CacheManager+getFile+downloadFile+readTxtFile)、downloadFile×2(AnalyzeUrl.type/`UrlUtil.getSuffix`+FileUtils.getPath/getCachePath+MD5Utils+AnalyzeUrl.getInputStream+`File.createFileReplace`)。移植 `model/analyzeRule/CustomUrl`(纯 JVM,用 AnalyzeUrl.paramPattern)、`utils/UrlUtil.getSuffix`(最小子集,去 Android/HttpURLConnection 耦合)。
    - ⏸️ 2c-4c = 压缩簇(getZip/Rar/7zByteArrayContent+String 包装+unzip/un7z/unrar/unArchiveFile/getTxtInFolder)**推迟**:ArchiveUtils(`android.net.Uri`/`androidx.documentfile.DocumentFile`/`appCtx`)、LibArchiveUtils(`android.os.ParcelFileDescriptor`/`android.system.Os*`/`me.zhanghai.android.libarchive` Android 包装)重度 Android 耦合;移植需换 JVM 压缩库(commons-compress)+ 重写,价值低(罕见书源特性)。`getZipByteArrayContent` 本身只用 JDK `ZipInputStream`(可单独移植),但 rar/7z 阻塞;暂整体推迟,留 Phase 1c switchover 时由 app 侧提供或后续单开。
    - ⏸️ 2c-4d = 字体簇(queryTTF×3/queryBase64TTF/replaceFont×2)**推迟**:依赖 app-only `AppCacheManager`(在 app `help/CacheManager.kt` 内,getQueryTTF/put 用 app 缓存)+ `QueryTTF.java`(字体解析)+ `toStringArray`。移植需把 AppCacheManager 抽 SPI 或 stub;价值低(罕见字体混淆书源)。暂推迟。
    - app-only(不进引擎):openVideoPlayer/openUrl/toast/longToast/getVerificationCode/startBrowser×3/getReadBookConfig(×2)/getThemeConfig(×2)/androidId。

**JsExtensions 引擎版现状**:HTTP(ajax/ajaxAll/ajaxTestAll/connect/get/post/head)、WebView(webView/webViewGetSource/webViewGetOverrideUrl)、Cookie(getCookie)、文件(getFile/readFile/readTxtFile/deleteFile/downloadFile/importScript/cacheFile)、utils(randomUUID/getWebViewUA/t2s/s2t/htmlFormat/toURL/toNumChapter/log/logType)+加密簇(JsEncodeUtils)+编码(strToBytes/hex/base64/encodeURI/timeFormat)已齐,覆盖绝大多数书源。压缩/字体簇推迟(app-only 或后续)。**至此 §9.5 A 路线 JsExtensions 主体落地,AnalyzeUrl/AnalyzeRule 已 flip 进引擎且 CI 双绿——Phase 1 最高风险核心已过。**
- **B**(未选):停在绿地基 + 骨架,把核心作后续聚焦。

**记忆**:`C:\Users\Mort\.claude\projects\e--GITHUB-readerMT\memory\monorepo-unify-progress.md` 同步维护,后续会话可续。