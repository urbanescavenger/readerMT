package com.htmake.reader

/**
 * :server 模块入口标记。
 *
 * Phase 0 仅为占位,证明 kotlin-jvm 工具链在该模块可用。
 * Phase 2 起此模块承载从 reader-mt 移植的服务端(Spring Boot + Vert.x),
 * 消费 :legado-engine 并提供平台实现 com.htmake.reader.platform.*。
 *
 * :server 禁止在 `io.legado.app` 包下定义类(CI 强制)。
 */
object ServerModule