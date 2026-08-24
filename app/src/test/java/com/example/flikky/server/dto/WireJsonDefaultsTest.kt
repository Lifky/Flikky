package com.example.flikky.server.dto

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 推给浏览器的 payload 必须**永远带齐所有字段**。
 *
 * 缺陷复现（v1.19.0 用户实测）：kotlinx 默认的 `Json` 伴生对象 `encodeDefaults = false`，
 * 会把值等于声明默认值的字段从 JSON 里删掉。浏览器端用 `hasOwnProperty` 判断
 * 「这次推送带没带这个字段」，缺席被读作「没变化」，于是「改回默认值」这个方向永远
 * 同步不了 —— 会话时间戳关不掉、消息操作样式切不回常驻按钮、允许对端撤回重新开启无效、
 * 收藏功能关不掉。只有 `recallEnabled`（唯一没有声明默认值的字段）表现正常。
 *
 * 详见 [WireJson] 的 KDoc。
 */
class WireJsonDefaultsTest {

    /** 全部取默认值的那个 PeerInfoDto —— 正是缺陷最严重的输入。 */
    private fun allDefaults() = PeerInfoDto(
        deviceName = "Flikky",
        phoneAvatarId = 0,
        backgroundMode = "DEFAULT",
        recallEnabled = true,
    )

    @Test
    fun `a fully-default peer info still serializes every field`() {
        val encoded = WireJson.encodeToString(PeerInfoDto.serializer(), allDefaults())
        val keys = Json.parseToJsonElement(encoded).jsonObject.keys

        // 这四个是用户实测报告的那四条，缺一个就是一条「开得了关不了」的缺陷。
        val reported = listOf(
            "sessionTimestampEnabled",
            "messageActionStyle",
            "allowPeerRecall",
            "favoriteEnabled",
        )
        assertEquals(
            "这些字段在「值等于默认值」时从 wire payload 里消失了，浏览器会把缺席读作" +
                "「没变化」并保留旧值 —— 用户看到的就是「改回默认值同步不了，刷新才好」。缺失：",
            emptyList<String>(),
            reported.filterNot { it in keys },
        )

        // 不只那四条：整个 DTO 的字段都必须在。写成「拿声明里的字段名逐个比」而不是
        // 数个数 —— 将来加字段时这条会自动覆盖它，不需要有人记得回来改。
        val declared = declaredPeerInfoFields()
        assertTrue(
            "从 PeerInfoDto 的声明里没解析出字段名，先修这个解析再看下面的断言",
            declared.size >= 15,
        )
        assertEquals(
            "PeerInfoDto 的这些字段没进 wire payload：",
            emptyList<String>(),
            declared.filterNot { it in keys },
        )
    }

    @Test
    fun `the bare Json companion would drop them - this is what the bug was`() {
        // 反面对照：钉住「为什么必须用 WireJson」。哪天有人把 WireJson 改成默认配置，
        // 上面那条会红；而这条保证「默认配置确实会丢字段」这个前提本身没被 kotlinx 改掉，
        // 免得上面那条在一个不再成立的理由上空转。
        val encoded = Json.encodeToString(PeerInfoDto.serializer(), allDefaults())
        val keys = Json.parseToJsonElement(encoded).jsonObject.keys
        assertTrue(
            "默认 Json 竟然带上了 sessionTimestampEnabled —— kotlinx 的 encodeDefaults " +
                "语义变了，WireJson 的存在理由需要重新论证",
            "sessionTimestampEnabled" !in keys,
        )
        assertTrue(
            "recallEnabled 没有声明默认值，任何配置下都必须在（它正是唯一表现正常的字段）",
            "recallEnabled" in keys,
        )
    }

    @Test
    fun `no wire payload is serialized with the bare Json companion`() {
        // 缺陷类的守卫：16 个调用点全都改了，但真正要防的是「下一个」。
        // server/ 与 service/ 里任何裸 Json.encodeToString 都会静默重现同一个缺陷。
        val offenders = mutableListOf<String>()
        for (dir in listOf("server", "service")) {
            root().resolve("main/java/com/example/flikky/$dir")
                .walkTopDown()
                .filter { it.extension == "kt" }
                .forEach { file ->
                    file.readLines().forEachIndexed { i, line ->
                        val bare = Regex("""(?<![\w.])Json\.encodeToString""").containsMatchIn(line) ||
                            line.contains("kotlinx.serialization.json.Json.encodeToString")
                        if (bare) offenders += "${file.name}:${i + 1}"
                    }
                }
        }
        assertEquals(
            "这些地方用裸 Json 伴生对象序列化 wire payload，会丢掉取默认值的字段" +
                "（浏览器把缺席读作「没变化」）。改用 WireJson。命中：",
            emptyList<String>(),
            offenders,
        )
    }

    private fun declaredPeerInfoFields(): List<String> {
        val body = root().resolve("main/java/com/example/flikky/server/dto/Dtos.kt")
            .readText()
            .substringAfter("data class PeerInfoDto(")
            .substringBefore("\n)")
        return Regex("""^\s*val ([A-Za-z0-9_]+):""", RegexOption.MULTILINE)
            .findAll(body).map { it.groupValues[1] }.toList()
    }

    /** 单测的工作目录是 app/，但直接跑模块时可能是仓库根 —— 两种都兜住。 */
    private fun root(): File = File("src").takeIf { it.isDirectory } ?: File("app/src")
}
