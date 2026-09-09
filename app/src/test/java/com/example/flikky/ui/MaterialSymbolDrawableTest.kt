package com.example.flikky.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Material Symbols 矢量图的**规格守卫**。
 *
 * 项目铁律：所有 icon 用官方最新图标库、**禁手搓**。gstatic 给的 24px SVG 是
 * `viewBox="0 -960 960 960"` —— path 坐标落在 `-960..0`，所以必须
 * 包一个 `<group android:translateY="960">` 把它平移回可视区，
 * 而 **path 本身逐字照抄、一个坐标都不动**。
 *
 * 手改坐标的后果不是报错，是**静默变形**：图形偏移或缩放一点点，
 * 和同一套里的其他图标放在一起才看得出不对。所以用守卫钉住三件事：
 *
 * 1. viewport 是 960×960（不是 24×24 —— 那是另一种导出风味，坐标系不同）；
 * 2. 有 `translateY="960"` 的 group（少了它图形整个跑出画布，只剩空白）；
 * 3. path 里没有小数坐标以外的可疑改动痕迹 —— 具体见下面那条的说明。
 *
 * 本文件是 2026-09-09 加 `ic_folder_eye` 时补的洞：此前「官方 path」这条铁律
 * 只写在 CLAUDE.md 与各文件的注释里，**没有任何测试拦着**。
 */
class MaterialSymbolDrawableTest {

    private fun drawableDir(): File {
        var dir: File? = File(System.getProperty("user.dir").orEmpty()).absoluteFile
        while (dir != null) {
            val a = File(dir, "src/main/res/drawable")
            if (a.isDirectory) return a
            val b = File(dir, "app/src/main/res/drawable")
            if (b.isDirectory) return b
            dir = dir.parentFile
        }
        error("cannot locate res/drawable from user.dir=" + System.getProperty("user.dir").orEmpty())
    }

    /**
     * 所有声明自己是 gstatic 风味（注释里写了 viewBox 0 -960 960 960）的矢量图，
     * **返回的正文已剥掉 XML 注释**。
     *
     * 剥注释是必需的，不是洁癖：这些文件的头部注释里逐字写着
     * `<group android:translateY="960">` 当说明。不剥的话「有没有那个 group」
     * 这条断言会匹配到注释 —— 把真正的 group 删掉都不会红（逼红实测零条红，
     * 而且是本会话第四次踩「源码守卫扫到了注释」这一族）。
     */
    private fun gstaticVectors(): List<Pair<String, String>> =
        drawableDir().listFiles { f -> f.name.endsWith(".xml") }
            .orEmpty()
            .map { it.name to it.readText() }
            .filter { (_, text) -> text.contains("viewBox 0 -960 960 960") }
            .map { (name, text) -> name to text.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "") }
            .sortedBy { it.first }

    @Test
    fun `there are gstatic-flavoured vectors to check`() {
        // 防切片失效：哪天注释措辞变了，上面那个 filter 会静默返回空列表，
        // 于是本文件所有断言都在零个文件上通过。
        assertTrue(
            "一个 gstatic 风味的矢量图都没找到 —— 注释措辞变了？先修切片再谈守卫",
            gstaticVectors().size >= 10,
        )
    }

    @Test
    fun `every gstatic vector keeps the 960 viewport`() {
        val wrong = gstaticVectors().filterNot { (_, text) ->
            text.contains("""android:viewportWidth="960"""") &&
                text.contains("""android:viewportHeight="960"""")
        }.map { it.first }
        assertEquals(
            "gstatic 的 24px SVG 是 960×960 坐标系。改成 24×24 而不动 path，" +
                "图形会放大 40 倍；这些文件的 viewport 不对：",
            emptyList<String>(),
            wrong,
        )
    }

    @Test
    fun `every gstatic vector wraps its path in the translateY group`() {
        // path 坐标在 -960..0，不平移的话整个图形在画布上方 —— 渲染出来是空白。
        // 这是「图标不见了」这类问题最常见的单一原因。
        val wrong = gstaticVectors().filterNot { (_, text) ->
            Regex("""<group\s+android:translateY="960"\s*>""").containsMatchIn(text)
        }.map { it.first }
        assertEquals(
            "这些 gstatic 矢量图没有 <group android:translateY=\"960\">，" +
                "path 会落在画布外、渲染成空白：",
            emptyList<String>(),
            wrong,
        )
    }

    @Test
    fun `every gstatic vector keeps its coordinates in the negative band`() {
        // 官方 path 的所有纵坐标都落在 -960..0。若有人「顺手把坐标平移到正区间」
        // 以省掉那个 translateY group，这一条会红 —— 那正是被禁止的手改。
        //
        // 判据是**整条 path 的纵坐标范围**，不是起点写法：官方导出既有大写 M
        // （绝对）也有小写 m（相对）开头，只看起点会把 10 个合法图标全判成违规
        // （写这条时的第一版就是这么错的）。
        val offenders = mutableListOf<String>()
        gstaticVectors().forEach { (name, text) ->
            val data = Regex("""android:pathData="([^"]+)"""").find(text)?.groupValues?.get(1)
            if (data == null) {
                offenders += "$name: 找不到 pathData"
                return@forEach
            }
            // 绝对纵坐标的可靠取样点：`V<y>` 与 `M<x><y>` / `L<x><y>` 的第二个数。
            val absoluteYs = Regex("""[MLV](-?[\d.]+)(?:[ ,](-?[\d.]+))?""")
                .findAll(data)
                .mapNotNull { m ->
                    if (m.groupValues[0].startsWith("V")) m.groupValues[1].toDoubleOrNull()
                    else m.groupValues[2].takeIf { it.isNotEmpty() }?.toDoubleOrNull()
                }
                .toList()
            if (absoluteYs.isEmpty()) return@forEach
            val bad = absoluteYs.filter { it > 0.5 || it < -960.5 }
            if (bad.isNotEmpty()) {
                offenders += "$name: 绝对纵坐标 ${bad.take(3)} 落在 -960..0 之外 —— 坐标被手改过"
            }
        }
        assertEquals(
            "官方 path 必须逐字照抄、坐标一个都不动（手改的后果是静默变形）：",
            emptyList<String>(),
            offenders,
        )
    }

    @Test
    fun `the hidden-files row uses the folder eye symbol`() {
        // 用户点名：「显示隐藏文件」用 Folder Eye。
        // 换回普通 folder 会与上一行「允许电脑浏览手机存储」撞图。
        val screen = File(
            drawableDir().parentFile.parentFile,
            "java/com/example/flikky/ui/settings/SettingsScreen.kt",
        ).readText()
        val at = screen.indexOf("R.string.settings_show_hidden)")
        assertTrue("设置页里找不到「显示隐藏文件」那一行", at > 0)
        val row = screen.substring(at, minOf(screen.length, at + 500))
        assertTrue(
            "「显示隐藏文件」的 leading icon 不是 ic_folder_eye：\n$row",
            row.contains("R.drawable.ic_folder_eye"),
        )
        assertTrue("ic_folder_eye.xml 不存在", File(drawableDir(), "ic_folder_eye.xml").isFile)
    }

    @Test
    fun `folder zip keeps the official path verbatim`() {
        val xml = File(drawableDir(), "ic_folder_zip.xml").readText()
            .replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        val official = "M640-480v-80h80v80h-80Zm0 80h-80v-80h80v80Zm0 80v-80h80v80h-80Z" +
            "M447-640l-80-80H160v480h400v-80h80v80h160v-400H640v80h-80v-80H447Z" +
            "M160-160q-33 0-56.5-23.5T80-240v-480q0-33 23.5-56.5T160-800h240l80 80h320" +
            "q33 0 56.5 23.5T880-640v400q0 33-23.5 56.5T800-160H160Zm0-80v-480 480Z"

        assertTrue("folder_zip path 与官方 Material Symbol 不同", xml.contains("android:pathData=\"$official\""))
    }

    @Test
    fun `interests outline keeps the official path verbatim`() {
        assertOfficialPath(
            "ic_interests.xml",
            "m80-520 200-360 200 360H80Zm87 353q-47-47-47-113 0-67 47-113.5T280-440" +
                "q66 0 113 47t47 113q0 66-47 113t-113 47q-66 0-113-47Zm169.5-56.5Q360-247 360-280" +
                "t-23.5-56.5Q313-360 280-360t-56.5 23.5Q200-313 200-280t23.5 56.5Q247-200 280-200" +
                "t56.5-23.5ZM216-600h128l-64-115-64 115Zm304 480v-320h320v320H520Zm80-80h160v-160H600v160Z" +
                "m80-320q-57-48-95.5-81T523-659q-23-25-33-47t-10-47q0-45 31.5-76t78.5-31q27 0 50.5 12.5" +
                "T680-813q16-22 39.5-34.5T770-860q47 0 78.5 31t31.5 76q0 25-10 47t-33 47q-23 25-61.5 58" +
                "T680-520Zm0-105q72-60 96-85t24-41q0-13-7.5-21t-20.5-8q-10 0-19.5 5.5T729-755l-49 47-49-47" +
                "q-14-14-23.5-19.5T588-780q-13 0-20.5 8t-7.5 21q0 16 24 41t96 85Zm0-78Zm-400 45Zm0 378Zm400 0Z",
        )
    }

    @Test
    fun `interests fill keeps the official path verbatim`() {
        assertOfficialPath(
            "ic_interests_fill.xml",
            "m80-520 200-360 200 360H80Zm200 400q-66 0-113-47t-47-113q0-66 47-113t113-47q66 0 113 47" +
                "t47 113q0 66-47 113t-113 47Zm240 0v-320h320v320H520Zm160-400q-57-48-95.5-81T523-659" +
                "q-23-25-33-47t-10-47q0-45 31.5-76t78.5-31q27 0 50.5 12.5T680-813q16-22 39.5-34.5" +
                "T770-860q47 0 78.5 31t31.5 76q0 25-10 47t-33 47q-23 25-61.5 58T680-520Z",
        )
    }

    private fun assertOfficialPath(name: String, official: String) {
        val file = File(drawableDir(), name)
        assertTrue("$name 不存在", file.isFile)
        val xml = file.readText().replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        assertTrue("$name path 与官方 Material Symbol 不同", xml.contains("android:pathData=\"$official\""))
    }
}
