package com.example.flikky.service

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 跨 rebind 引用规范的**源码**守卫（`CLAUDE.md` §"跨 Wi-Fi rebind 的引用规范"）。
 *
 * ## 为什么需要它，[TransferControllerRebindReferenceTest] 还不够
 *
 * 那个测试验的是「`() -> WsHub?` 这种 lambda 在 hub 被替换后能跟上」——
 * 它在测试里自己构造 lambda，**完全不读生产源码**。于是它只能拦住一种改坏：
 * 把参数类型改回 `WsHub`（那会编译失败）。
 *
 * 而 v1.2 踩了三次的那个 bug 形状不是类型错，是**闭包捕获局部变量**：
 *
 * ```kotlin
 * val server = buildTransferKtor(...)      // startTransfer 的局部变量
 * controller = TransferController(
 *     wsHub = { server.wsHub },            // ❌ 类型对，lambda 也在，但捕获死引用
 * )
 * ```
 *
 * 这份写法类型完全正确、编译通过、行为测试全绿 —— 2026-09-19 审查时实测把
 * `{ ktor?.wsHub }` 改成 `{ server.wsHub }`，整组 rebind 测试**零红**。
 * rebind 之后广播会继续打到已废弃的 hub，浏览器静默收不到任何 APP→浏览器消息。
 *
 * 所以这里读源码、断言形状：`.wsHub` 的每一次访问都必须经由 `ktor` field 现取。
 */
class TransferServiceRebindSourceTest {

    @Test
    fun `every wsHub access goes through the ktor field, never a captured local`() {
        val service = productCode("service/TransferService.kt")
        val accesses = Regex("""(\w+)(\?|!!)?\.wsHub""").findAll(service).toList()

        assertTrue("sanity: 源码里一个 .wsHub 都没扫到，判据失效了", accesses.isNotEmpty())
        accesses.forEach { match ->
            val receiver = match.groupValues[1]
            assertEquals(
                "跨 rebind 的 wsHub 必须现取 ktor field，不能捕获 '$receiver'：${match.value}",
                "ktor",
                receiver,
            )
        }
    }

    @Test
    fun `the controller receives wsHub as a lambda over the field`() {
        val service = productCode("service/TransferService.kt")

        assertTrue(
            "TransferController 的 wsHub 实参必须是 { ktor?.wsHub }",
            Regex("""wsHub\s*=\s*\{\s*ktor\?\.wsHub\s*\}""").containsMatchIn(service),
        )
    }

    @Test
    fun `the local server handle is only started and stored, never subscribed to`() {
        // 局部变量本身是合法的（绑定端口时 field 还没赋值），受限的是它的用途。
        val service = productCode("service/TransferService.kt")
        val locals = Regex("""val (\w+) = build\w*Ktor\(""").findAll(service)
            .map { it.groupValues[1] }
            .toSet()

        assertTrue("sanity: 没扫到 buildXxxKtor 的局部句柄", locals.isNotEmpty())
        locals.forEach { name ->
            // `(?<![.\w])` 排除包名限定：`com.example.flikky.server.routes.X` 里的
            // `server.routes` 不是这个局部句柄。2026-09-19 首版漏了它，守卫在正确代码上转红。
            Regex("""(?<![.\w])$name\.(\w+)""").findAll(service).forEach { use ->
                assertTrue(
                    "局部 Ktor 句柄 '$name' 只允许 start()/stop()，发现 $name.${use.groupValues[1]}",
                    use.groupValues[1] in setOf("start", "stop"),
                )
            }
        }
    }

    @Test
    fun `the guards fire on the v1_2 bug shape`() {
        // 自检：合成那份「类型对、lambda 在、但捕获局部」的坏代码，三条判据都要抓到。
        val bad = """
            val handle = com.example.flikky.server.routes.FavoriteFileHandle(x)
            val server = buildTransferKtor(ip, auth)
            controller = TransferController(wsHub = { server.wsHub })
            scope.launch { server.wsHub.broadcast("status", payload) }
        """.trimIndent()

        val receivers = Regex("""(\w+)(\?|!!)?\.wsHub""").findAll(bad)
            .map { it.groupValues[1] }
            .toSet()
        assertEquals("判据必须把 server 认出来", setOf("server"), receivers)
        assertFalse(
            "坏样本不该匹配 { ktor?.wsHub }",
            Regex("""wsHub\s*=\s*\{\s*ktor\?\.wsHub\s*\}""").containsMatchIn(bad),
        )
        assertTrue(
            "坏样本里局部句柄被订阅了，第三条判据必须能看到 server.wsHub",
            Regex("""(?<![.\w])server\.wsHub""").containsMatchIn(bad),
        )
        // 反向自检：判据必须**只**盯局部句柄，不能把包名 `flikky.server.routes` 当成它。
        // 少了这条负向后查，第三条判据会在完全正确的代码上转红（首版就是）。
        val uses = Regex("""(?<![.\w])server\.(\w+)""").findAll(bad)
            .map { it.groupValues[1] }
            .toSet()
        assertFalse("包名限定的 server.routes 不该被算成局部句柄的用法", "routes" in uses)
    }

    private fun productCode(relative: String): String {
        val file = File("src/main/java/com/example/flikky/$relative")
            .takeIf { it.isFile }
            ?: File("app/src/main/java/com/example/flikky/$relative")
        assertTrue("source file not found: $relative", file.isFile)
        return stripCommentsAndImports(file.readText())
    }

    private fun stripCommentsAndImports(source: String): String = source
        .replace(Regex("""/\*[\s\S]*?\*/"""), "")
        .replace(Regex("""//[^\r\n]*"""), "")
        .replace(Regex("""(?m)^\s*import\s+[^\r\n]+\r?\n"""), "")
}
