package com.example.flikky.ui.serving

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 快捷设置的收录完整性守卫。
 *
 * 会话运行期间底部「设置」tab 被锁（MainActivity 的 `settingsEnabled = !servingActive`），
 * 所以**凡是「双端同步」的设置项，只要不在快捷设置里，整场会话就都调不了**——双端同步
 * 这个能力也就发挥不出来。用户实测报的正是这个：有些同步项当时不在快捷设置里。
 *
 * 判据是机械的：`PeerInfoDto` 的字段就是「会同步」的定义，而 `toPeerInfoDto` 把
 * `FlikkySettings` 映射进去。所以「某个设置会同步」等价于「它出现在 toPeerInfoDto 的
 * 映射里」。本测试从源码取出这份映射，再要求 QuickSettingsSheet 里有对应入口。
 *
 * 这是一个读源码的守卫测试（本模块既有先例：MainActivityManifestTest、
 * KtorServerFavoritesWiringTest）。之所以不写成 Compose UI 测试：这里要断言的是
 * 「有没有漏项」，而漏项恰恰意味着那个控件不存在——UI 测试只能断言存在的东西。
 * 真正的价值在于**将来给 PeerInfoDto 加字段时它会自动失效**，而不是复述当前状态。
 */
class QuickSettingsCoverageTest {

    /**
     * `toPeerInfoDto` 里出现的 FlikkySettings 属性 → 快捷设置中对应的证据串。
     *
     * 证据串刻意选「非它不可」的东西（setter 回调名或行标题的资源 id），而不是随便一个
     * 子串——否则改名之后断言还绿着，就成了摆设。
     */
    private val syncedSettingToEvidence = mapOf(
        // 主题色三兄弟一起决定 themeSeed，共用主题选择器入口
        "themeMode" to "onOpenThemePicker",
        "presetTheme" to "onOpenThemePicker",
        "customThemeSeedArgb" to "onOpenThemePicker",
        "darkMode" to "onSetDarkMode",
        "amoled" to "onSetAmoled",
        "phoneAvatarKey" to "onOpenAvatarPicker",
        "background" to "onOpenBackgroundPicker",
        "deviceName" to "onSetDeviceName",
        "bubbleCornerRadius" to "onSetBubbleCorner",
        "avatarGrouping" to "onSetAvatarGrouping",
        "messageActionStyle" to "onSetMessageActionStyle",
        "animationSpeed" to "onSetAnimationSpeed",
        "sessionTimestampEnabled" to "onSetSessionTimestamp",
        "recallBetaEnabled" to "onSetRecallBeta",
        "allowPeerRecall" to "onSetAllowPeerRecall",
        "favoriteBetaEnabled" to "onSetFavoriteBeta",
    )

    /**
     * `toPeerInfoDto` 里出现但**不是**用户可调设置的属性，不需要快捷设置入口。
     * 单列出来而不是从判据里悄悄漏掉——漏掉和「明确豁免」在一年后读起来完全不同。
     */
    private val notUserAdjustable = setOf(
        "phoneAvatarId",   // 旧版数值头像，已被 phoneAvatarKey 取代，仅为兼容保留
    )

    @Test
    fun `every synced setting has a quick-settings entry`() {
        val mapping = toPeerInfoDtoBody()
        val quickSheet = quickSettingsSource()

        val referenced = FLIKKY_SETTINGS_PROPERTIES.filter { prop ->
            Regex("\\b$prop\\b").containsMatchIn(mapping)
        }
        assertTrue(
            "toPeerInfoDto 的映射体里一个 FlikkySettings 属性都没认出来——" +
                "大概是切片标记漂了，先修这里再看下面的断言。",
            referenced.size >= 10,
        )

        val missing = referenced
            .filterNot { it in notUserAdjustable }
            .filterNot { prop ->
                val evidence = syncedSettingToEvidence[prop]
                evidence != null && quickSheet.contains(evidence)
            }
        assertEquals(
            "这些设置会同步到浏览器，但快捷设置里没有入口。会话期间设置页锁着，" +
                "所以它们整场会话都调不了。给 QuickSettingsSheet 补上对应入口，" +
                "并在本测试的 syncedSettingToEvidence 里登记；确实不该由用户调的" +
                "请登记到 notUserAdjustable 并说明理由。缺口：",
            emptyList<String>(),
            missing,
        )
    }

    @Test
    fun `the evidence table has no stale rows`() {
        // 反方向：登记表里若留着已经不再同步的项，它就在替一个不存在的需求辩护。
        val mapping = toPeerInfoDtoBody()
        val stale = syncedSettingToEvidence.keys.filterNot { prop ->
            Regex("\\b$prop\\b").containsMatchIn(mapping)
        }
        assertEquals(
            "这些项已不在 toPeerInfoDto 的映射里，登记表该清掉：",
            emptyList<String>(),
            stale,
        )
    }

    @Test
    fun `settings the browser cannot follow stay out of quick settings`() {
        // 快捷设置的价值来自「这里的每一项都会同步」这个承诺。掺进浏览器跟不了的项
        // （需要 PIN、历史保留、屏幕常亮……），它就退化成把设置页搬了一份过来。
        val mapping = toPeerInfoDtoBody()
        val quickSheet = quickSettingsSource()
        val nonSynced = listOf(
            "requirePin",
            "historyRetainLimit",
            "allowBackDuringSession",
            "keepScreenOnDuringSession",
            "sortMode",
            "groupMode",
            "autoCheckUpdate",
        )
        for (prop in nonSynced) {
            assertTrue(
                "$prop 竟然出现在 toPeerInfoDto 里了——那它就是同步项，" +
                    "请把它移进 syncedSettingToEvidence 而不是留在这条断言里。",
                !Regex("\\b$prop\\b").containsMatchIn(mapping),
            )
            assertTrue(
                "$prop 浏览器跟不了，不该出现在快捷设置里。",
                !quickSheet.contains(prop),
            )
        }
    }

    // ── 源码切片 ────────────────────────────────────────────────────────────

    private fun toPeerInfoDtoBody(): String {
        val source = sourceFile("service/TransferService.kt")
        val start = source.indexOf("fun FlikkySettings.toPeerInfoDto(")
        assertTrue("toPeerInfoDto 不在 TransferService.kt 里了", start >= 0)
        val end = source.indexOf("return PeerInfoDto(", start)
        assertTrue("toPeerInfoDto 的 return 不见了", end > start)
        // 取到 PeerInfoDto( 的收尾括号：局部变量（resolvedDark / seed / mode）也算映射的一部分
        val close = source.indexOf("\n        }", end)
        assertTrue("toPeerInfoDto 的结尾找不到", close > end)
        return source.substring(start, close)
    }

    /**
     * 只取 QuickSettingsSheet 的**函数体**，不含参数列表。
     *
     * 第一版直接搜整个文件，于是「声明了 onSetAmoled 这个参数」就足以让断言通过——
     * 而一个声明了回调却从不接线的 sheet 正是缺陷本身。参数列表必须排除掉，
     * 断言的才是「真的有控件调它」。
     */
    private fun quickSettingsSource(): String {
        val source = sourceFile("ui/serving/QuickSettingsSheet.kt")
        val decl = source.indexOf("fun QuickSettingsSheet(")
        assertTrue("QuickSettingsSheet 的声明找不到了", decl >= 0)
        val bodyStart = source.indexOf("\n) {", decl)
        assertTrue("QuickSettingsSheet 的参数列表结尾找不到", bodyStart > decl)
        return source.substring(bodyStart)
    }

    private fun sourceFile(relative: String): String {
        val file = File("src/main/java/com/example/flikky/$relative")
            .takeIf { it.isFile }
            ?: File("app/src/main/java/com/example/flikky/$relative")
        assertTrue("找不到源码文件：$relative", file.isFile)
        return file.readText()
    }

    private companion object {
        /** FlikkySettings 的属性名，从声明里现取——写死一份会跟着模型漂。 */
        val FLIKKY_SETTINGS_PROPERTIES: List<String> by lazy {
            val source = File("src/main/java/com/example/flikky/data/settings/FlikkySettings.kt")
                .takeIf { it.isFile }
                ?: File("app/src/main/java/com/example/flikky/data/settings/FlikkySettings.kt")
            val body = source.readText()
                .substringAfter("data class FlikkySettings(")
                .substringBefore("\n)")
            Regex("""^\s*val ([A-Za-z0-9_]+):""", RegexOption.MULTILINE)
                .findAll(body)
                .map { it.groupValues[1] }
                .toList()
        }
    }
}
