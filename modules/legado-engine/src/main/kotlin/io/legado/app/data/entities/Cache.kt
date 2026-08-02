package io.legado.app.data.entities

/**
 * 缓存 DTO(引擎版):剥离 Room `@Entity`/`@PrimaryKey`/`@Index`。
 *
 * 引擎 HTTP 层(CacheManager / HttpHelper)使用;Android `:app` 保留带 Room 注解的
 * `Cache` 副本(数据库 schema)直至 Phase 1c switchover,届时改 `CacheEntity` + 与本 DTO 的映射。
 */
data class Cache(
    val key: String = "",
    var value: String? = null,
    var deadline: Long = 0L
)