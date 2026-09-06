package com.example.flikky.ui.serving.storage

import com.example.flikky.util.DirectoryScan
import com.example.flikky.util.ScannedEntry
import com.example.flikky.util.SortSpec
import com.example.flikky.util.StorageListingPolicy
import com.example.flikky.util.StoragePathPolicy
import java.io.File
import java.net.URLConnection
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 列表里的一项。
 *
 * [relativePath] 是 UI 的 key 与选择集合的元素，不能只用 [name]——两个不同目录下的同名文件
 * 会互相顶掉。[absolutePath] 是 Coil 加载缩略图要用的真实路径：只有相对路径时 Coil 打不开文件，
 * 而失败是**静默**的（回落成图标容器），媒体行看起来只是"没有缩略图"，没人会发现。
 */
data class LocalEntry(
    val name: String,
    val relativePath: String,
    val absolutePath: String,
    val isDir: Boolean,
    val size: Long,
    val mtime: Long,
    val mime: String?,
    val restricted: Boolean,
    /**
     * 目录的直接子项数；文件为 null。
     *
     * 两端副标题必须一致：服务端 DTO 一直给的是项数（浏览器显示「13 项」），
     * 而 App 端首版显示的是固定文案「文件夹」——同一个目录在手机上和电脑上
     * 说的不是一件事。代价是每个目录行一次 readdir，所以列举必须在 IO 上跑
     * （见 ServingViewModel.openStorageDir）。
     */
    val childCount: Int? = null,
)

/**
 * 流式列举的一步。
 *
 * 分三种而不是「一个可空列表」：路径校验失败（[Failed]）与「读到了但是空目录」
 * 在 UI 上是两句话，而 [Done] 让调用方知道流是**正常结束**的而不是被截断的。
 */
sealed interface StorageChunk {
    /** 路径已确认可读。[path] 是规范化后的相对路径，UI 拿它定面包屑。 */
    data class Head(val path: String) : StorageChunk

    /** 一批条目，已在全局顺序中就位。追加到列表尾部即可，不需要重排。 */
    data class Batch(val entries: List<LocalEntry>) : StorageChunk

    /** 全部条目已产出。 */
    data object Done : StorageChunk

    /** 进不去：路径非法 / 不存在 / 不是目录 / 系统沙箱。 */
    data object Failed : StorageChunk
}

/** 操作条摘要。[skipped] 涵盖「路径非法 / 已被删 / 是目录」三种情况。 */
data class StorageSelectionSummary(
    val count: Int,
    val totalBytes: Long,
    val skipped: Int,
)

/**
 * 一次目录列举的结果。[selected] 由调用方持有并跨目录累积，这里只做纯变换。
 *
 * [loading] 是「正在列举」。大目录的列举要几百毫秒到几秒，没有这个标志用户点了
 * 完全看不出有反应（装机验收实测），只能靠 UI 画一条进度。迁移规则见 [StorageNavigation]。
 */
data class LocalStorageState(
    val path: String,
    val entries: List<LocalEntry>,
    val selected: Set<String> = emptySet(),
    val loading: Boolean = false,
    /**
     * 最新一批在 [entries] 里的起始下标。
     *
     * UI 用它算逐行入场的阶梯序号：`index - lastBatchStart`。放在状态里而不是让 UI
     * 自己记，是因为「哪些行是刚追加进来的」只有产生这批数据的一侧知道；
     * UI 侧靠 `remember` 去猜会在重组或配置变更后错位。
     */
    val lastBatchStart: Int = 0,

    /**
     * 从缓存恢复时要滚回哪里（列表下标 + 该行内的像素偏移）。
     *
     * `-1` 表示「不是恢复，从顶部开始」。用下标而不是像素滚动量：LazyColumn 的
     * 行高不定，同一个像素值在不同字号 / 屏幕上指向不同的行。
     */
    val restoredScrollIndex: Int = -1,
    val restoredScrollOffset: Int = 0,
)

/**
 * App 端本机共享存储浏览器：路径栈 + 选择集合的纯逻辑。不走 HTTP，直接读 [java.io.File]。
 *
 * ## 为什么过滤排序必须委派给 [StorageListingPolicy]
 *
 * App 端在手机上看到的顺序，和浏览器端在电脑上看到的顺序，必须一致。
 * 在这里自写一个比较器即便当下结果相同，服务端将来调整规则时也不会跟上，
 * 而**两端各自的测试都会是绿的**——这类不一致极难被发现。v1.18.0 的时间戳
 * 靠「双端同构纯函数」拿到一致性，这里照此办。
 *
 * ## 为什么本地路径也要过 [StoragePathPolicy]
 *
 * 这个类不面向网络，但路径拼接错误一样会读到不该读的地方（`..` 穿越、
 * 指向根外的符号链接）。守卫已经现成，绕过它只是省一行调用。
 *
 * 无 Android 依赖，可在 `test/` 直接跑（mime 用 [URLConnection] 而非 Android 的 MimeTypeMap）。
 */
class LocalStorageBrowser(
    private val root: File,
    /** 用户是否打开了「显示隐藏文件」。每次列举现取，理由同 SharedStorageBrowser。 */
    private val showHidden: () -> Boolean = { false },
    /**
     * 当前排序。lambda 而不是值，理由同 [showHidden]：用户随时可改，
     * 而这个对象活在 ViewModel 里、生命周期比一次设置变更长得多。
     */
    private val sortSpec: () -> SortSpec = { SortSpec.NameAsc },
) {

    /**
     * 列举 [relative] 下的内容。返回 null 表示**不给进**：路径非法、不存在、不是目录，
     * 或落在系统锁死的沙箱里。UI 拿到 null 就停在原地，不要自动回退——
     * 自动回退会在「目录刚被删掉」时变成反复请求。
     */
    fun list(relative: String): LocalStorageState? {
        val dir = StoragePathPolicy.resolve(root, relative) ?: return null
        val path = StoragePathPolicy.relativize(root, dir)
        // 沙箱目录本身可以被**列出**（上一级的列表里要显示它），但不能被**进入**。
        if (StorageListingPolicy.isRestricted(path)) return null
        if (!dir.isDirectory) return null
        val children = dir.listFiles()?.toList() ?: return null
        val ordered = StorageListingPolicy.filterAndSort(
            children,
            { it.isDirectory },
            { it.name },
            showHidden(),
            // 目录恒报 0：文件系统给的目录大小各平台不一致（Windows 给 0、
            // ext4 通常给 4096），对用户也无意义。不统一的话「按大小排」时
            // 目录之间的顺序会随平台变，而名称兜底只在 size 相等时才生效。
            // 与 `ScannedEntry` 的同名裁决一致。
            { if (it.isDirectory) 0L else it.length() },
            { it.lastModified() },
            sortSpec(),
        )
        return LocalStorageState(
            path = path,
            entries = ordered.map { child ->
                val childPath = if (path.isEmpty()) child.name else "$path/${child.name}"
                val isDir = child.isDirectory
                LocalEntry(
                    name = child.name,
                    relativePath = childPath,
                    absolutePath = child.absolutePath,
                    isDir = isDir,
                    size = if (isDir) 0L else child.length(),
                    mtime = child.lastModified(),
                    mime = if (isDir) null else URLConnection.guessContentTypeFromName(child.name),
                    restricted = StorageListingPolicy.isRestricted(childPath),
                    // list() 而不是 listFiles()：只要个数，不需要为每个子项建 File 对象。
                    childCount = if (isDir) StorageListingPolicy.visibleCount(child.list(), showHidden()) else null,
                )
            },
        )
    }

    /**
     * 流式列举 [relative]：先确认路径，再分批产出条目。
     *
     * ## 为什么要流式
     *
     * 上千项的目录一次性列举要几百毫秒到几秒，用户看到的是一条转很久的进度条
     * （装机验收原话：「进度条会加载很久」）。分批之后首批 24 行几十毫秒就到，
     * 之后列表持续向下生长——观感从「等半天然后一次性弹出」变成「加载极快」。
     *
     * ## 顺序与首屏的取舍
     *
     * 排序是「目录优先 + 名称」，所以第一行是谁取决于**所有**条目的 isDir。
     * [DirectoryScan.scan] 先做一遍廉价扫描（每条 1 次 stat 而不是 3 次）并排好序，
     * 之后才分批。首屏等的是这一遍扫描，不是全部元数据。裁决见类注释与 D37。
     *
     * ## 按批懒算的是什么
     *
     * `childCount` —— 每个子目录一次 readdir，目录密集的文件夹里这是最贵的一项。
     * 只为**当前这一批**里的目录算，于是它随列表生长逐步填上。
     *
     * ## 取消
     *
     * 每批 `emit` 是天然的取消检查点；扫描内部的紧循环没有挂起点，所以传
     * `ensureActive` 进去当检查钩子。少了它，用户点进大目录又立刻退出时，
     * 那次扫描还会跑完几千次系统调用（用户要求的「避免无效开销」）。
     * 调用方必须在 [kotlinx.coroutines.Dispatchers.IO] 上收集。
     */
    fun listStream(relative: String): Flow<StorageChunk> = flow {
        val dir = StoragePathPolicy.resolve(root, relative)
        if (dir == null) {
            emit(StorageChunk.Failed)
            return@flow
        }
        val path = StoragePathPolicy.relativize(root, dir)
        // 沙箱目录本身可以被列出（上一级的列表里要显示它），但不能被进入。
        if (StorageListingPolicy.isRestricted(path)) {
            emit(StorageChunk.Failed)
            return@flow
        }
        // 先把 context 取出来：钩子不是 suspend 的（扫描内部是普通紧循环），
        // 而 CoroutineContext.ensureActive() 是普通扩展函数，捕获后可以在里面调。
        val ctx = currentCoroutineContext()
        val scanned = DirectoryScan.scan(dir, showHidden(), sortSpec()) { ctx.ensureActive() }
        if (scanned == null) {
            emit(StorageChunk.Failed)
            return@flow
        }
        emit(StorageChunk.Head(path))
        for (batch in DirectoryScan.batches(scanned)) {
            currentCoroutineContext().ensureActive()
            emit(StorageChunk.Batch(batch.map { toEntry(it, path, dir) }))
        }
        emit(StorageChunk.Done)
    }

    /** [ScannedEntry] → [LocalEntry]。`childCount` 在这里算，所以它是按批发生的。 */
    private fun toEntry(scanned: ScannedEntry, parentPath: String, parentDir: File): LocalEntry {
        val childPath =
            if (parentPath.isEmpty()) scanned.name else "$parentPath/${scanned.name}"
        val child = File(parentDir, scanned.name)
        return LocalEntry(
            name = scanned.name,
            relativePath = childPath,
            absolutePath = child.absolutePath,
            isDir = scanned.isDir,
            size = scanned.size,
            mtime = scanned.mtime,
            mime = if (scanned.isDir) {
                null
            } else {
                URLConnection.guessContentTypeFromName(scanned.name)
            },
            restricted = StorageListingPolicy.isRestricted(childPath),
            // list() 而不是 listFiles()：只要个数，不需要为每个子项建 File 对象。
            childCount = if (scanned.isDir) StorageListingPolicy.visibleCount(child.list(), showHidden()) else null,
        )
    }

    /**
     * 上一级的相对路径，根目录返回 **null**。
     *
     * 返回 null 而不是 `""` 是刻意的：`BackHandler` 的 `enabled` 直接用它判断，
     * 根目录返回 `""` 会让返回键在根目录也被拦住，用户困在文件 tab 里出不去。
     */
    fun parentOf(relative: String): String? {
        val trimmed = relative.trim().trim('/')
        if (trimmed.isEmpty()) return null
        val cut = trimmed.lastIndexOf('/')
        return if (cut < 0) "" else trimmed.substring(0, cut)
    }

    /** 单击勾选：在集合里就移除，不在就加入。跨目录累积由调用方持有集合实现。 */
    fun toggle(selected: Set<String>, relativePath: String): Set<String> =
        if (relativePath in selected) selected - relativePath else selected + relativePath

    /**
     * 操作条那一行「已选 N 项 · 合计大小」的数据，以及跳过数。
     *
     * **必须与 [resolveExisting] 算出同一个结果**——两处各判一遍是分叉的起点：
     * 摘要说「已选 2 项 · 8 KB」而实际只发出 1 个文件，用户会以为传输丢了数据，
     * 而两边各自的测试都能是绿的。因此这里直接复用 [resolveExisting]，不另写筛选。
     * 守卫见 `StorageSendSummaryTest.the summary agrees with what resolveExisting will actually send`。
     */
    fun selectionSummary(selected: Collection<String>): StorageSelectionSummary {
        val (files, skipped) = resolveExisting(selected)
        return StorageSelectionSummary(
            count = files.size,
            totalBytes = files.sumOf { it.length() },
            skipped = skipped,
        )
    }

    /**
     * 把选中的相对路径解析成真实文件，返回「存在的文件」与「跳过数」。
     *
     * 跳过的三种情况：路径非法、文件已不存在、目标是目录。
     * 目录那条不是多余的——选择集合理论上只装文件，但一旦混进目录，
     * `offerStoredFile` 会拿到一个目录并以 0 字节发出去，两端都不报错。
     * 跳过数要报给 snackbar（与 v1.17.1 收藏批量操作一致），不能静默丢弃。
     */
    fun resolveExisting(relativePaths: Collection<String>): Pair<List<File>, Int> {
        var skipped = 0
        val files = mutableListOf<File>()
        for (p in relativePaths) {
            val f = StoragePathPolicy.resolve(root, p)
            if (f == null || !f.isFile) {
                skipped++
                continue
            }
            files += f
        }
        return files to skipped
    }
}

/** 点一行会发生什么。UI 只许在这三个分支上分派，不许自己再判 `isDir` / `restricted`。 */
enum class StorageRowAction {
    /** 目录：进入。**不进选择集合**——裁决 B 保留「单击即勾选」但只对文件生效。 */
    OPEN,

    /** 文件：切换勾选。跨目录累积。 */
    TOGGLE,

    /** 系统锁死的沙箱目录：不可点、不可进、不可选。行仍要显示，用户得知道它存在。 */
    NONE,
}

/**
 * 行为决策抽成纯函数的理由：这三条（目录不可选 / 文件单击即选 / 沙箱行完全惰性）
 * 全都是「写错了照样编译、照样跑」的规则，而验它们最自然的地方是仪器测试——
 * 那要立整屏 + ViewModel + ServiceLocator。抽出来后在 `test/` 秒级可验，
 * 且 UI 侧只剩一个 `when`，把「判断」和「渲染」分开。
 *
 * `restricted` 必须在 `isDir` **之前**判：沙箱目录同时满足两者，
 * 顺序颠倒就会让 `Android/data` 变成可进入的普通目录。
 */
fun storageRowAction(entry: LocalEntry): StorageRowAction = when {
    entry.restricted -> StorageRowAction.NONE
    entry.isDir -> StorageRowAction.OPEN
    else -> StorageRowAction.TOGGLE
}

/** 面包屑的一级。[path] 为空表示根。 */
data class StorageCrumb(val label: String, val path: String)

/**
 * 面包屑的分级与折叠规则。**与浏览器端 `panel-files.js` 的 `breadcrumbSegments` 同构**：
 * 总级数 ≤ 4 时全显示；超过则首级 + `…` + 末两级。
 *
 * 返回列表里的 `null` 就是那个 `…` 占位——UI 渲染成不可点的省略号。
 * 两端各写一遍的后果是「手机上折叠、电脑上不折叠」，而两边测试都绿。
 *
 * [rootLabel] 由调用方给（i18n），纯函数本身不碰资源，所以能在 `test/` 直接跑。
 */
fun breadcrumbSegments(path: String, rootLabel: String): List<StorageCrumb?> {
    val parts = path.trim().trim('/').split('/').filter { it.isNotEmpty() }
    val all = buildList {
        add(StorageCrumb(rootLabel, ""))
        parts.forEachIndexed { i, name ->
            add(StorageCrumb(name, parts.take(i + 1).joinToString("/")))
        }
    }
    if (all.size <= 4) return all
    return buildList {
        add(all.first())
        add(null)
        addAll(all.takeLast(2))
    }
}

/**
 * 目录切换的方向：进入还是返回。给转场动效定方向用。
 *
 * 判据是**层级深度**，不是字符串长度、也不是前缀关系。
 * - 长度会被名字长短骗：`DCIM` → `Music` 算「进入」，`DCIM` → `A` 算「返回」，
 *   而两者都是同一层的平移。浏览器端逼红时实测到这个。
 * - 前缀在「进入 / 返回上一级」上是对的，但平移到同深度的兄弟目录会一律判成
 *   「返回」，而那更像横向切换，按「进入」更自然。
 *
 * 两端共用这一个函数，动效方向才不会一边进一边退。
 */
object StorageNavigationDirection {

    /** 从 [from] 走到 [to] 算不算「前进」（进入更深或同层平移）。 */
    fun forward(from: String, to: String): Boolean = depth(to) >= depth(from)

    /** 相对路径的层级深度。根为 0；前后与重复分隔符都不计。 */
    fun depth(path: String): Int =
        path.trim().trim('/').split('/').count { it.isNotEmpty() }
}
