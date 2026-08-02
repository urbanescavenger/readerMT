package io.legado.app.data.entities

/**
 * Cookie DTO(引擎版):剥离 Room `@Entity`/`@PrimaryKey`/`@Index`。
 *
 * 引擎 HTTP 层(CookieStore / HttpHelper / AnalyzeUrl)使用;Android `:app` 保留带 Room
 * 注解的 `Cookie` 副本直至 Phase 1c switchover,届时改 `CookieEntity` + 映射。
 */
data class Cookie(
    var url: String = "",
    var cookie: String = ""
)