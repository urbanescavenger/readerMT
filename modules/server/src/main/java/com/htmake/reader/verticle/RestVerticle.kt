package com.htmake.reader.verticle

import io.vertx.core.http.HttpMethod
import io.vertx.ext.web.Route
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.CorsHandler
import io.vertx.ext.web.handler.LoggerFormat
import io.vertx.ext.web.handler.LoggerHandler
import io.vertx.ext.web.handler.SessionHandler
import io.vertx.ext.web.sstore.LocalSessionStore
import io.vertx.kotlin.coroutines.CoroutineVerticle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mu.KotlinLogging
import com.htmake.reader.api.ReturnData
import com.htmake.reader.utils.error
import com.htmake.reader.utils.gson
import com.htmake.reader.utils.success
import java.net.URLDecoder


private val logger = KotlinLogging.logger {}

/** 请求体上限(字节)。原版 Vert.x 3 无上限,迁移到 4 后默认只有 10MB,导入大书会被 413 拒掉。 */
private const val MAX_REQUEST_BODY_SIZE = 500L * 1024 * 1024

abstract class RestVerticle : CoroutineVerticle() {

    protected lateinit var router: Router

    open var port: Int = 8080

    override suspend fun start() {
        super.start()
        router = Router.router(vertx)
        val cookieName = "reader.session"
	    router.route().handler(
            SessionHandler.create(LocalSessionStore.create(vertx))
                            .setSessionCookieName(cookieName)
                            .setSessionTimeout(7L * 86400 * 1000)
                            .setSessionCookiePath("/")
        );
        router.route().handler {
            it.addHeadersEndHandler { _ ->
                val cookie = it.getCookie(cookieName)
                if (cookie != null) {
                    // 每次访问都延长cookie有效期
                    cookie.setMaxAge(2L * 86400 * 1000)
                    cookie.setPath("/")
                }
            }
            it.next()
        }

        // CORS support
        router.route().handler {
            it.addHeadersEndHandler { _ ->
                val origin = it.request().getHeader("Origin")
                if (origin != null && origin.isNotEmpty()) {
                    var res = it.response()
                    res.putHeader("Access-Control-Allow-Origin", origin)
                    res.putHeader("Access-Control-Allow-Credentials", "true")
                    res.putHeader("Access-Control-Allow-Methods", "GET, POST, PATCH, PUT, DELETE")
                    res.putHeader("Access-Control-Allow-Headers", "Authorization, Content-Type, If-Match, If-Modified-Since, If-None-Match, If-Unmodified-Since, X-Requested-With")
                }
            }
            val origin = it.request().getHeader("Origin")
            if (origin != null && origin.isNotEmpty() && it.request().method() == HttpMethod.OPTIONS) {
                it.removeCookie(cookieName)
                it.success("")
            } else {
                it.next()
            }
        }

        // Vert.x 4 的 BodyHandler 新增了 10MB 默认上限(BodyHandler.DEFAULT_BODY_LIMIT),
        // Vert.x 3 时代(本服务端原版)是无上限的。迁移到 4 之后,超过 10MB 的请求体
        // (导入本地书籍的 txt/epub、备份恢复等)会被 BodyHandler 直接 `context.fail(413)`,
        // 前端只看到 "Request failed with status code 500"(413 被下面 failureHandler 的 NPE 盖掉)。
        // 书籍/备份动辄几十 MB,这里放宽到 500MB;仍保留有限上限,避免超大 JSON 撑爆堆。
        router.route().handler(BodyHandler.create().setBodyLimit(MAX_REQUEST_BODY_SIZE))

        router.route().handler(LoggerHandler.create(LoggerFormat.DEFAULT));
        router.route("/reader3/*").handler {
            // Vert.x 4 移除 rawMethod(),改 method().name()(返回大写方法名)
            logger.info("{} {}", it.request().method().name(), URLDecoder.decode(it.request().absoluteURI(), "UTF-8"))
            // GET/POST 无 body 时 bodyAsString 为 null,判空再取长度(否则 .length NPE)
            val body = it.bodyAsString
            if (!it.request().method().name().equals("PUT") && (it.fileUploads() == null || it.fileUploads().isEmpty()) && !body.isNullOrEmpty() && body.length < 1000) {
                logger.info("Request body: {}", body)
            }
            it.next()
        }

        router.get("/health").handler { it.success("ok!") }

        initRouter(router)

//        router.errorHandler(500) { routerContext ->
//            logger.error { routerContext.failure().message }
//            routerContext.error(routerContext.failure())
//        }

        router.route().last().failureHandler { ctx ->
            val failure = ctx.failure()
            if (failure != null) {
                ctx.error(failure)
            } else {
                // 状态码型失败(BodyHandler 的 413 请求体超限、417 等)不带 Throwable,
                // ctx.failure() 为 null。原先直接 ctx.error(ctx.failure()) 会在 Kotlin 非空
                // 参数检查处抛 NPE("Unhandled exception in router"),把真实状态码盖成 500。
                val status = ctx.statusCode()
                logger.warn("request failed: status {}, uri: {}", status, ctx.request().absoluteURI())
                if (!ctx.response().ended()) {
                    val errorMsg = if (status == 413) {
                        "请求体超过服务端上限(${MAX_REQUEST_BODY_SIZE / 1024 / 1024}MB)"
                    } else {
                        "请求失败(status $status)"
                    }
                    ctx.response()
                            .setStatusCode(status)
                            .putHeader("content-type", "application/json; charset=utf-8")
                            .end(gson.toJson(ReturnData().setErrorMsg(errorMsg)))
                }
            }
        }

        logger.info("port: {}", port)
        vertx.createHttpServer().requestHandler(router).exceptionHandler{error ->
            onException(error)
        }.listen(port) { res ->
            if (res.succeeded()) {
                logger.info("Server running at: http://localhost:{}", port);
                logger.info("Web reader running at: http://localhost:{}", port);
                started();
            } else {
                onStartError();
            }
        }
    }

    abstract suspend fun initRouter(router: Router);

    open fun onException(error: Throwable) {
        logger.error("vertx exception: {}", error)
    }

    open fun onStartError() {
    }

    open fun started() {

    }

    open fun onHandlerError(ctx: RoutingContext, error: Exception) {
        logger.error("Error: {}", error)
        ctx.error(error)
    }

    /**
     * An extension method for simplifying coroutines usage with Vert.x Web routers
     */
    fun Route.coroutineHandler(fn: suspend (RoutingContext) -> Any) {
        handler { ctx ->
            val job = launch(Dispatchers.IO) {
                try {
                    ctx.success(fn(ctx))
//                    fn(ctx)
                } catch (e: Exception) {
                    onHandlerError(ctx, e)
                }
            }
        }
    }

    fun Route.coroutineHandlerWithoutRes(fn: suspend (RoutingContext) -> Any) {
        handler { ctx ->
            val job = launch(Dispatchers.IO) {
                try {
                    fn(ctx)
                } catch (e: Exception) {
                    onHandlerError(ctx, e)
                }
            }
        }
    }
}