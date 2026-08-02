package io.legado.app

/**
 * :legado-engine 模块入口标记。
 *
 * Phase 0 仅为占位,证明 kotlin-jvm 工具链在该模块可用。
 * Phase 1 起此模块承载平台无关的 legado 引擎(书源解析/搜索/目录/正文/缓存等),
 * 两端(Android :app 与 :server)共用,平台差异经 `io.legado.app.platform.*` SPI 注入。
 *
 * 引擎源码禁止 import `android.*`/`androidx.*`/`com.script.*`/`com.htmake.*`(CI 强制)。
 */
object EngineModule