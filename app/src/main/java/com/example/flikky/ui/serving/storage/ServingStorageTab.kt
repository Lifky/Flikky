package com.example.flikky.ui.serving.storage

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.flikky.R
import com.example.flikky.ui.components.FileLeadingSpec
import com.example.flikky.ui.components.FileLeadingVisual
import com.example.flikky.ui.components.flikkyItemAnimation
import com.example.flikky.ui.components.FlikkyFloatingToolbarLift
import com.example.flikky.ui.components.StoredVideo
import com.example.flikky.ui.components.formatSize
import com.example.flikky.ui.files.FileCategory
import com.example.flikky.ui.files.FilesListBuilder
import com.example.flikky.ui.files.iconResource
import com.example.flikky.ui.theme.Motion
import com.example.flikky.ui.theme.Spacing
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 会话页「文件」tab：本机共享存储浏览器（只读）。
 *
 * ## 这个 composable 刻意不知道 `storageBrowsingEnabled`
 *
 * `storageBrowsingEnabled` 是**给对端浏览器用的**主开关（默认关）。App 端浏览自己的存储
 * 只受系统权限约束。四态矩阵里最容易写错的一格是「开关关 + 已授权」——
 * 用同一个布尔量把两端一起门控，会让用户在自己手机上也看不到文件，
 * 而设置项的文案说的是「允许电脑端浏览」。
 *
 * 因此这里连参数都不收：拿不到的东西没法误用。守卫见 `ui/serving/ServingTabsStructureTest`。
 *
 * ## 行的视觉必须与文件总览页 / 收藏页零差异
 *
 * 行首走共用件 [FileLeadingVisual]，行体走 [SegmentedListItem] +
 * `ListItemDefaults.segmentedShapes/segmentedColors`，配色实参与
 * `ui/files/FilesScreen` 的文件行逐项相同。自己写一套 `Box` + `Icon` 就是
 * v1.17.1 花一整轮消掉的那种重复。
 *
 * 勾选态不用 checkbox（裁决 B）：全项目 list 行没有一个 checkbox，
 * 多选态既有画法是「行底 primaryContainer + leading 容器翻浅色」。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ServingStorageTab(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    state: LocalStorageState,
    summary: StorageSelectionSummary,
    onOpenDir: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onScrollChanged: (String, Int, Int) -> Unit = { _, _, _ -> },
    onRefresh: () -> Unit = {},
    onClearSelection: () -> Unit,
    onSendSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!hasPermission) {
        StoragePermissionCard(onRequestPermission = onRequestPermission, modifier = modifier)
        return
    }
    // 操作条是**悬浮 overlay**，必须作为内容区 Box 的子节点并对齐 BottomCenter。
    // 放进 Scaffold 的 bottomBar 槽位会预留等高空白把列表顶走
    // （FlikkySelectingToolbarOverlay 的 KDoc 记着这个 bug）。
    val listState = rememberLazyListState()

    /**
     * 已经为哪个目录恢复过滚动位置了。
     *
     * 必须 `rememberSaveable`：普通 `remember` 会跟着离屏销毁一起消失，
     * 与 effect 的 key 同时重建，等于没有标记。
     */
    var restoredFor by rememberSaveable { mutableStateOf("") }

    // ── 回退时把位置放回去 ──────────────────────────────────────────────────
    //
    // restoredScrollIndex 为 -1 表示这不是一次恢复（新目录从顶部开始）。
    // 等 entries 到位再滚：内容还没有的时候 scrollToItem 会被夹在可滚范围里。
    // 用 `snapshotFlow` 而不是直接在组合里滚 —— 后者会在每次重组时重复执行。
    LaunchedEffect(state.path, state.restoredScrollIndex, state.entries.size) {
        val target = state.restoredScrollIndex
        if (target < 0 || state.entries.isEmpty()) return@LaunchedEffect
        // **每个目录只恢复一次。**
        //
        // HorizontalPager 会把离屏的页从组合里移除。切到「会话」再切回来，
        // 这个 composable 重新进入组合、effect 的 key 一个没变，于是又跑一遍 ——
        // 把用户从他刚滚到的位置弹回缓存里记的旧位置。
        // 而 rememberLazyListState 的位置本身会被 pager 的 SaveableStateHolder 存下来，
        // 回来时列表本来就在用户离开的地方，不需要也不该再恢复。
        if (restoredFor == state.path) return@LaunchedEffect
        restoredFor = state.path
        listState.scrollToItem(
            index = target.coerceAtMost(state.entries.size - 1),
            scrollOffset = state.restoredScrollOffset,
        )
    }

    // 位置变化上报给 ViewModel，进缓存时一起存。只在停下来时报，滚动中每帧都报
    // 会把状态写成一条噪声流。
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling ->
                if (!scrolling) {
                    // 带上路径：位置属于**哪个目录**必须一起报，否则调用方无从
                    // 判断这份位置是不是它要存的那个目录的。
                    onScrollChanged(
                        state.path,
                        listState.firstVisibleItemIndex,
                        listState.firstVisibleItemScrollOffset,
                    )
                }
            }
    }

    val slideProgress = remember { Animatable(1f) }
    val slideSpec = Motion.spatialFast<Float>()
    val slideDistance = with(LocalDensity.current) { Spacing.xl.toPx() }
    var slideSign by remember { mutableFloatStateOf(1f) }
    var previousPath by remember { mutableStateOf("") }
    var lastSlidPath by remember { mutableStateOf<String?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            StorageBreadcrumb(
                path = state.path,
                onNavigate = onOpenDir,
                onRefresh = onRefresh,
            )
            // ── 目录切换的方向横移 ──────────────────────────────────────────
            //
            // 进目录从右滑入、返回从左滑入，方向走两端共用的 StorageNavigationDirection。
            //
            // **等第一批条目到达才启动**：容器刚建好时还空着，那时跑动画等于演给一个
            // 空盒子看（浏览器端实测到这个 —— 224ms 的横移在第一批到达前就跑完了）。
            // 也刻意不用 AnimatedContent：那会在过渡期间同时保留两份内容，
            // 而这里只需要一个位移，不需要新旧同屏。
            LaunchedEffect(state.path, state.entries.isNotEmpty()) {
                if (state.entries.isEmpty()) return@LaunchedEffect
                // 同一路径只演一次：刷新（授权完成、回到前台）不该让列表再滑一下。
                if (lastSlidPath == state.path) return@LaunchedEffect
                slideSign = if (StorageNavigationDirection.forward(previousPath, state.path)) 1f else -1f
                previousPath = state.path
                lastSlidPath = state.path
                slideProgress.snapTo(0f)
                slideProgress.animateTo(1f, slideSpec)
            }

            // 流式列举期间进度条一直在，但**不再挡住列表**：只要已经有行到达就把它
            // 收成顶部一条细线，行照常显示并继续向下生长。
            // 首版是「loading 就整片显示进度条」，那与流式追加冲突——列表永远看不见。
            if (state.loading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = Spacing.screenEdge,
                            vertical = if (state.entries.isEmpty()) Spacing.xl else Spacing.sm,
                        ),
                )
            }
            if (state.entries.isEmpty() && state.loading) {
                // 首批还没到：只有进度条，不显示「这个文件夹是空的」——那句话此刻是假的。
                Spacer(Modifier.weight(1f))
            } else if (state.entries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.serving_storage_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = (1f - slideProgress.value) * slideDistance * slideSign
                            alpha = slideProgress.value
                        },
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    // 底部留出浮动操作条的高度，否则最后一行永远被它压住、选不到。
                    contentPadding = PaddingValues(
                        top = Spacing.sm,
                        bottom = if (state.selected.isEmpty()) {
                            Spacing.sectionGap
                        } else {
                            FlikkyFloatingToolbarLift
                        },
                    ),
                ) {
                    itemsIndexed(state.entries, key = { _, e -> e.relativePath }) { index, entry ->
                        // 只有共用件这一层：`animateItem` 管增删与重排，由 lazy 布局
                        // 按 key 自己跟踪，不受 item 回收影响。
                        // **刻意不做逐行入场**——那需要行在入场前不占高度，而零高会
                        // 破坏 lazy 视口填充（丢行、必须下拉才出现、切 tab 卡顿）。
                        // 「一注流水」的节奏由流式批次之间的间隔提供。
                        Box(modifier = flikkyItemAnimation()) {
                        StorageEntryRow(
                            entry = entry,
                            index = index,
                            count = state.entries.size,
                            selected = entry.relativePath in state.selected,
                            onOpenDir = onOpenDir,
                            onToggleSelection = onToggleSelection,
                        )
                        }
                    }
                    // 终止标记。流式加载下「列表停止生长」与「加载完了」在屏幕上
                    // 长得一样，用户无从判断自己是不是看到了全部（装机验收原话）。
                    // 加载中报已到数量，完成后报总数——两者都给出确定的语义。
                    item(key = FOOTER_KEY) {
                        Text(
                            text = if (state.loading) {
                                stringResource(
                                    R.string.serving_storage_loading_count,
                                    state.entries.size,
                                )
                            } else {
                                stringResource(R.string.serving_storage_total, state.entries.size)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = Spacing.screenEdge,
                                    vertical = Spacing.lg,
                                ),
                        )
                    }
                }
            }
        }
        // 官方 MD3 FAB 菜单，右下角。**不是** floating toolbar：那个组件的 content
        // 契约是「一串 IconButton」，塞进选中计数这类自由文本会把容器撑成一个
        // 巨型椭圆（装机验收 Screenshot_4）。计数改放进菜单项文案，见 StorageSelectionFab。
        StorageSelectionFab(
            summary = summary,
            selected = state.selected,
            currentPath = state.path,
            onClear = onClearSelection,
            onSend = onSendSelection,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(Spacing.lg),
        )
    }
}

/**
 * 面包屑。分级与折叠规则走纯函数 [breadcrumbSegments]（与浏览器端同构），
 * 这里只负责画：字号 `labelLarge` + 官方 `chevron_right` 作分隔符（裁决 E），
 * 当前级不可点。MD3 没有 breadcrumb 组件（本地 44 个组件文档均无），全自绘。
 */
@Composable
private fun StorageBreadcrumb(
    path: String,
    onNavigate: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    // 这里曾有一层 AnimatedContent 让面包屑随路径横移淡入。
    // 2026-09-02 用户裁决去掉：面包屑本来就短、变化幅度小，动效意义不大，
    // 而「我进到别处了」这个空间感由**列表整体**的方向横移表达（见 ServingStorageTab）。
    StorageBreadcrumbRow(path, onNavigate, onRefresh)
}

@Composable
private fun StorageBreadcrumbRow(
    path: String,
    onNavigate: (String) -> Unit,
    onRefresh: () -> Unit = {},
) {
    val rootLabel = stringResource(R.string.serving_storage_root)
    val moreLabel = stringResource(R.string.serving_storage_breadcrumb_more)
    val crumbs = remember(path, rootLabel) { breadcrumbSegments(path, rootLabel) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenEdge, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        crumbs.forEachIndexed { index, crumb ->
            if (index > 0) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
            val isCurrent = index == crumbs.lastIndex
            val clickable = crumb != null && !isCurrent
            Text(
                text = crumb?.label ?: moreLabel,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isCurrent) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = if (clickable) {
                    Modifier
                        .clickable { onNavigate(crumb!!.path) }
                        .padding(Spacing.xs)
                } else {
                    Modifier.padding(Spacing.xs)
                },
            )
        }
        // 目录缓存刻意不做自动刷新（在子目录待久了父目录可能已变，自动重取会把
        // 「秒回」变回「每次都等」），代价就是必须给用户一个手动的出口。
        // 放在面包屑行末尾：它是**当前目录**这一行的动作，与路径同处一行。
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onRefresh) {
            Icon(
                painter = painterResource(R.drawable.ic_refresh),
                contentDescription = stringResource(R.string.serving_storage_refresh),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 一行。[FileLeadingVisual] + [SegmentedListItem]，配色实参与文件总览页逐项相同。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StorageEntryRow(
    entry: LocalEntry,
    index: Int,
    count: Int,
    selected: Boolean,
    onOpenDir: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
) {
    val action = storageRowAction(entry)
    val category = FilesListBuilder.categoryOf(entry.mime)
    val shapes = ListItemDefaults.segmentedShapes(index = index, count = count)
    val colors = ListItemDefaults.segmentedColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        supportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        leadingContentColor = MaterialTheme.colorScheme.primary,
        selectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        selectedSupportingContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        selectedLeadingContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )
    val leading: @Composable () -> Unit = {
        when {
            entry.restricted -> LockedLeading()
            entry.isDir -> FileLeadingVisual(
                iconRes = R.drawable.ic_folder,
                thumbnailModel = null,
                selected = selected,
            )
            else -> FileLeadingVisual(
                iconRes = category.iconResource(),
                thumbnailModel = if (FilesListBuilder.isMedia(entry.mime)) {
                    remember(entry.absolutePath, category) {
                        // 只喂本地 File model。禁 coil-network（红线：无运行时外联）。
                        val f = File(entry.absolutePath)
                        if (category == FileCategory.VIDEO) StoredVideo(f) else f
                    }
                } else {
                    null
                },
                selected = selected,
            )
        }
    }
    val supporting: @Composable () -> Unit = {
        Text(
            text = when {
                entry.restricted -> stringResource(R.string.serving_storage_restricted)
                // 项数而不是固定文案「文件夹」：与浏览器端同一句话（服务端 DTO 一直给项数）。
                entry.isDir -> entry.childCount
                    ?.let { stringResource(R.string.serving_storage_items, it) }
                    ?: stringResource(R.string.serving_storage_folder)
                else -> formatSize(entry.size) + " · " + formatEntryDate(entry.mtime)
            },
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    val headline: @Composable () -> Unit = {
        Text(
            text = entry.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    // 目录行尾 chevron 表示"可进入"。文件行没有尾部件——单击整行即勾选，
    // 再放一个按钮会让"点哪里"变得不确定。
    val trailing: (@Composable () -> Unit)? = if (action == StorageRowAction.OPEN) {
        {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        null
    }
    val rowModifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = Spacing.screenEdge)

    // 三个分支全部由 storageRowAction 决定，这里不再自己判 isDir / restricted。
    when (action) {
        StorageRowAction.NONE -> SegmentedListItem(
            onClick = {},
            enabled = false,
            shapes = shapes,
            colors = colors,
            modifier = rowModifier,
            verticalAlignment = FileLeadingSpec.rowAlignment,
            leadingContent = leading,
            supportingContent = supporting,
            content = headline,
        )
        StorageRowAction.OPEN -> SegmentedListItem(
            onClick = { onOpenDir(entry.relativePath) },
            shapes = shapes,
            colors = colors,
            modifier = rowModifier,
            verticalAlignment = FileLeadingSpec.rowAlignment,
            leadingContent = leading,
            supportingContent = supporting,
            content = headline,
            trailingContent = trailing,
        )
        StorageRowAction.TOGGLE -> SegmentedListItem(
            selected = selected,
            onClick = { onToggleSelection(entry.relativePath) },
            shapes = shapes,
            colors = colors,
            modifier = rowModifier,
            verticalAlignment = FileLeadingSpec.rowAlignment,
            leadingContent = leading,
            supportingContent = supporting,
            content = headline,
        )
    }
}

/** 沙箱目录的 leading：同占位（[FileLeadingSpec.size]）的锁图标，保证 headline 起点不左右跳。 */
@Composable
private fun LockedLeading() {
    Box(
        modifier = Modifier
            .size(FileLeadingSpec.size)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = RoundedCornerShape(8.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_lock),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(FileLeadingSpec.iconSize),
        )
    }
}

/**
 * 权限解释卡。变体裁决 D：filled 路线 + `surfaceContainerHigh`、零阴影，
 * 与 `ConnectionInfoCard` / `OptionCard` 同一画法（官方 filled 用的是
 * `surfaceContainerHighest`，本项目既有约定是 High，跟项目走）。
 *
 * 文案如实说明「Android 没有只读版的该权限」——不含糊其辞是这张卡存在的理由。
 */
@Composable
private fun StoragePermissionCard(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(Spacing.sectionGap),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(Spacing.sectionGap),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text(
                    text = stringResource(R.string.serving_storage_permission_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.serving_storage_permission_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onRequestPermission,
                    modifier = Modifier.align(Alignment.End),
                ) { Text(stringResource(R.string.serving_storage_permission_grant)) }
            }
        }
    }
}

private val entryDateFormat = SimpleDateFormat("yy/MM/dd HH:mm", Locale.getDefault())

private fun formatEntryDate(mtime: Long): String = entryDateFormat.format(Date(mtime))

/**
 * 页脚 item 的稳定 key。
 *
 * 必须给：不给 key 的 item 用位置当身份，而位置随 entries 增长一直在变，
 * 于是每来一批都会被当成「删掉旧的、加一个新的」，`animateItem` 跟着演一遍淡出淡入。
 * 也不能用条目路径那套 —— 这一行不是条目。
 */
private const val FOOTER_KEY = "flikky-storage-footer"
