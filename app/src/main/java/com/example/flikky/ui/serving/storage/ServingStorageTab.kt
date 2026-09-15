package com.example.flikky.ui.serving.storage

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.flikky.R
import com.example.flikky.ui.components.FileLeadingSpec
import com.example.flikky.ui.components.FileLeadingVisual
import com.example.flikky.ui.components.ImagePreviewDialog
import com.example.flikky.ui.components.SortMenuAction
import com.example.flikky.ui.components.StoredVideo
import com.example.flikky.ui.components.flikkyItemAnimation
import com.example.flikky.ui.components.openResolvedFile
import com.example.flikky.util.formatBytes
import com.example.flikky.ui.files.FileCategory
import com.example.flikky.ui.files.FilesListBuilder
import com.example.flikky.ui.files.iconResource
import com.example.flikky.ui.theme.Motion
import com.example.flikky.ui.theme.Spacing
import com.example.flikky.util.SortKey
import com.example.flikky.util.SortSpec
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 会话页「文件」tab：本机共享存储浏览器（只读）。
 *
 * ## 对端门控只控制通道锁
 *
 * `storageBrowsingEnabled` 是**给对端浏览器用的**主开关（默认关）。App 端浏览自己的存储
 * 只受系统权限约束。四态矩阵里最容易写错的一格是「开关关 + 已授权」——
 * 用同一个布尔量把两端一起门控，会让用户在自己手机上也看不到文件，
 * 而设置项的文案说的是「允许电脑端浏览」。
 *
 * 因此 [peerStorageEnabled] 只供 [StorageChannelLockFab] 显示和切换通道，绝不参与内容渲染。
 * 守卫见 `ui/serving/ServingTabsStructureTest`。
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
@Composable
fun ServingStorageTab(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    state: LocalStorageState,
    summary: StorageSelectionSummary,
    peerStorageEnabled: Boolean,
    onSetPeerStorageEnabled: (Boolean) -> Unit,
    onOpenDir: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onScrollChanged: (String, Int, Int) -> Unit = { _, _, _ -> },
    onRefresh: () -> Unit = {},
    sortSpec: SortSpec = SortSpec.NameAsc,
    onPickSort: (SortKey) -> Unit = {},
    query: String = "",
    onQueryChange: (String) -> Unit = {},
    onClearSelection: () -> Unit,
    onSendSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!hasPermission) {
        StoragePermissionCard(onRequestPermission = onRequestPermission, modifier = modifier)
        return
    }
    val context = LocalContext.current
    var previewImage by remember { mutableStateOf<File?>(null) }
    fun openOrPreview(entry: LocalEntry) {
        val file = File(entry.absolutePath)
        if (FilesListBuilder.categoryOf(entry.mime) == FileCategory.IMAGE && file.exists()) {
            previewImage = file
        } else {
            openResolvedFile(
                context = context,
                file = file,
                displayName = entry.name,
                mime = entry.mime,
            )
        }
    }
    // 操作条是**悬浮 overlay**，必须作为内容区 Box 的子节点并对齐 BottomCenter。
    // 放进 Scaffold 的 bottomBar 槽位会预留等高空白把列表顶走
    // （FlikkySelectingToolbarOverlay 的 KDoc 记着这个 bug）。

    // 过滤后的行。列表、计数、空态**全用这一份** —— 用 state.entries 判空会在
    // 「目录非空但没有命中」时显示「这个文件夹是空的」，那句话此刻是假的。
    val shown = remember(state.entries, query) { StorageNavigation.filter(state.entries, query) }

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
                sortSpec = sortSpec,
                onPickSort = onPickSort,
                query = query,
                onQueryChange = onQueryChange,
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
            // 加载完成时**向上缩回**，而不是硬切消失：硬切的话下面的列表会瞬间
            // 跳上来一整条的高度。高度参与动画（只淡出的话列表照样硬跳），
            // 弹簧走 spatial —— 与主页 chips 分组的显隐同一档。
            //
            // 这里的 AnimatedVisibility 是安全的：它在 LazyColumn **之外**，
            // 不是 lazy item，所以不会踩那条「零高度破坏视口填充」的禁令
            // （守卫 LazyItemHeightConventionTest 只管 items(...) 的正文）。
            AnimatedVisibility(
                visible = state.loading,
                enter = expandVertically(Motion.spatial()) + fadeIn(Motion.effects()),
                // 不回弹：收到 0 的弹簧会弹回来，看着像故障（见 Motion.spatialFastNoBounce）
                exit = shrinkVertically(Motion.spatialFastNoBounce()) + fadeOut(Motion.effectsFast()),
            ) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = Spacing.screenEdge,
                            vertical = if (shown.isEmpty()) Spacing.xl else Spacing.sm,
                        ),
                )
            }
            if (shown.isEmpty() && state.loading) {
                // 首批还没到：只有进度条，不显示「这个文件夹是空的」——那句话此刻是假的。
                Spacer(Modifier.weight(1f))
            } else if (shown.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        // 「没有匹配的文件」与「这个文件夹是空的」是两句不同的话。
                        // 混用会让用户以为目录真的空了。
                        text = if (query.isNotBlank()) {
                            stringResource(R.string.serving_storage_search_empty)
                        } else {
                            stringResource(R.string.serving_storage_empty)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                // 按路径 key：**不同目录的行不是同一个列表。**
                //
                // 少了这个 key，换目录会被当成「旧目录全部删除 + 新目录全部插入」，
                // 而 `animateItem` 的 fadeOutSpec 会把消失的 item 继续组合、
                // 画在它原来的偏移上直到淡出跑完 —— 同一帧里两份列表都在画，
                // 偏移还不同，看起来就是两份列表错位叠着（装机验收两次都看到这个）。
                //
                // 加缓存之前这条路走不到：换目录会先经过 `entries.isEmpty() && loading`
                // 那个分支，那里整个 LazyColumn 都不在，旧行是直接销毁的。缓存让状态
                // 从「A 的条目」直接变成「B 的条目」，一个此前不存在的转换。
                //
                // 同目录内（流式追加、刷新）key 不变，仍然逐项 diff ——
                // 那才是 animateItem 该管的事。
                // key 里带上排序：改排序不改路径，若只按 path 分，
                // 列表状态会存活下来、停在旧下标上。带上它就自然从顶部开始
                // （初值 restoredScrollIndex 已被 resort 清成 -1）。
                key(state.path, sortSpec.format(), query) {
                // 每个目录**一份自己的**滚动状态。
                //
                // 放在 composable 顶层的话它跟着面板而不是跟着目录，新目录会直接
                // 继承上一个目录的 firstVisibleItemIndex —— 装机验收「在父目录往下
                // 滚 3 行再进子目录，子目录从第 4 项开始显示」就是这个。
                // 又一次「一份状态没带着它属于谁」。
                //
                // 位置由**初值**给定：秒回时给记忆位置，全新目录给 0。于是第一帧
                // 就在正确位置 —— 不需要事后 scrollToItem，也没有中间帧的跳动，
                // 而且两套机制争同一个位置的可能性从根上消失了。
                //
                // 切 tab 回来时 rememberLazyListState 自己的 saveable 会胜出
                // （初值只在没有存档时才用），所以用户离开时的位置照样保住。
                val listState = rememberLazyListState(
                    initialFirstVisibleItemIndex = state.restoredScrollIndex.coerceAtLeast(0),
                    initialFirstVisibleItemScrollOffset =
                        if (state.restoredScrollIndex >= 0) state.restoredScrollOffset else 0,
                )

                // 位置变化上报给 ViewModel，进缓存时一起存。只在停下来时报，
                // 滚动中每帧都报会把状态写成一条噪声流。
                LaunchedEffect(listState) {
                    snapshotFlow { listState.isScrollInProgress }
                        .collect { scrolling ->
                            if (!scrolling) {
                                // 带上路径：位置属于**哪个目录**必须一起报，
                                // 否则调用方无从判断这份位置是不是它要存的那个目录的。
                                onScrollChanged(
                                    state.path,
                                    listState.firstVisibleItemIndex,
                                    listState.firstVisibleItemScrollOffset,
                                )
                            }
                        }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // 只位移，**不淡入**。
                            //
                            // 列表按路径 key 之后旧行是一帧内销毁的，再从 alpha = 0
                            // 起淡入就会露出一个空帧 —— 装机验收「进出文件夹时
                            // listitem 整体闪了一下」。两个单独都对的决定
                            // （key 掉旧列表、横移带淡入）碰在一起产生了第三个现象。
                            //
                            // 空间感本来就靠位移给，淡入是多余的那一半。
                            translationX = (1f - slideProgress.value) * slideDistance * slideSign
                        },
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    // 底部留出浮动操作条的高度，否则最后一行永远被它压住、选不到。
                    contentPadding = PaddingValues(
                        top = Spacing.sm,
                        bottom = StorageSelectionFabSize + Spacing.xxxl,
                    ),
                ) {
                    itemsIndexed(shown, key = { _, e -> e.relativePath }) { index, entry ->
                        // 只有共用件这一层：`animateItem` 管增删与重排，由 lazy 布局
                        // 按 key 自己跟踪，不受 item 回收影响。
                        // **刻意不做逐行入场**——那需要行在入场前不占高度，而零高会
                        // 破坏 lazy 视口填充（丢行、必须下拉才出现、切 tab 卡顿）。
                        // 「一注流水」的节奏由流式批次之间的间隔提供。
                        // testTag 供仪器测试断言**真实布局**（行两两不重叠）。
                        // 那一类缺陷在 v1.20.0 出现过三次、每次根因不同，而纯逻辑
                        // 测试与源码扫描都看不见布局 —— 见 StorageRowOverlapTest。
                        Box(
                            modifier = flikkyItemAnimation()
                                .testTag(StorageRowTestTag),
                        ) {
                        StorageEntryRow(
                            entry = entry,
                            index = index,
                            count = shown.size,
                            selected = entry.relativePath in state.selected,
                            onOpenDir = onOpenDir,
                            onToggleSelection = onToggleSelection,
                            onPreview = ::openOrPreview,
                        )
                        }
                    }
                    // 终止标记。流式加载下「列表停止生长」与「加载完了」在屏幕上
                    // 长得一样，用户无从判断自己是不是看到了全部（装机验收原话）。
                    // 加载中报已到数量，完成后报总数——两者都给出确定的语义。
                    item(key = FOOTER_KEY) {
                        Text(
                            // 加载中报**目录的真实进度**（state.entries），
                            // 完成后报**当前看到的条数**（shown）。
                            // 两个计数问的是不同的问题：前者是「还在读」，
                            // 后者是「这一屏一共这么多」。过滤态下混用会让
                            // 「共 200 项」配着 3 行显示。
                            text = if (state.loading) {
                                stringResource(
                                    R.string.serving_storage_loading_count,
                                    state.entries.size,
                                )
                            } else {
                                stringResource(R.string.serving_storage_total, shown.size)
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
        }
        // Two buttons share the menu's fixed button slot; only the lock's X changes.
        StorageSelectionFab(
            summary = summary,
            selected = state.selected,
            currentPath = state.path,
            onClear = onClearSelection,
            onSend = onSendSelection,
            channelLock = { lockModifier ->
                StorageChannelLockFab(
                    peerEnabled = peerStorageEnabled,
                    onToggle = onSetPeerStorageEnabled,
                    modifier = lockModifier,
                )
            },
            modifier = Modifier
                .align(Alignment.BottomEnd),
        )
    }
    previewImage?.let { file ->
        ImagePreviewDialog(file = file, onDismiss = { previewImage = null })
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
    sortSpec: SortSpec,
    onPickSort: (SortKey) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
) {
    // 这里曾有一层 AnimatedContent 让面包屑随路径横移淡入。
    // 2026-09-02 用户裁决去掉：面包屑本来就短、变化幅度小，动效意义不大，
    // 而「我进到别处了」这个空间感由**列表整体**的方向横移表达（见 ServingStorageTab）。
    //
    // 搜索是**原地换行**而不是多加一行：文件 tab 是 pager 里的一页、上方还有 tabs，
    // 垂直空间宝贵。与 FilesScreen 的 searchActive 同一模式。
    var searching by rememberSaveable { mutableStateOf(false) }
    // 换目录时关键词被清空（见 openStorageDir），搜索行也跟着收起 ——
    // 留着一个空输入框占住面包屑，用户就看不到自己在哪了。
    LaunchedEffect(path) { searching = false }

    if (searching) {
        StorageSearchRow(
            query = query,
            onQueryChange = onQueryChange,
            onExit = {
                onQueryChange("")
                searching = false
            },
        )
    } else {
        StorageBreadcrumbRow(
            path = path,
            onNavigate = onNavigate,
            onRefresh = onRefresh,
            sortSpec = sortSpec,
            onPickSort = onPickSort,
            onStartSearch = { searching = true },
        )
    }
}

/** 搜索态的面包屑行：整行换成一个输入框。 */
@Composable
private fun StorageSearchRow(
    query: String,
    onQueryChange: (String) -> Unit,
    onExit: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    // 点了搜索按钮就该能直接打字，不用再点一次输入框。
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenEdge, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            placeholder = { Text(stringResource(R.string.serving_storage_search)) },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_search),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
        )
        IconButton(onClick = onExit) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = stringResource(R.string.serving_storage_search_exit),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StorageBreadcrumbRow(
    path: String,
    onNavigate: (String) -> Unit,
    onRefresh: () -> Unit = {},
    sortSpec: SortSpec = SortSpec.NameAsc,
    onPickSort: (SortKey) -> Unit = {},
    onStartSearch: () -> Unit = {},
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
        // 面包屑必须占**自己那一格**并可横向滚动。
        //
        // 早先它直接摊在这个 Row 里，靠末尾一个 `Spacer(weight(1f))` 把按钮推到
        // 右边 —— 路径一长，面包屑就把整行撑满、Spacer 只能拿到 0，右侧的
        // 搜索 / 排序 / 刷新被推出屏幕，完全点不到（2026-09-08 装机反馈）。
        //
        // `weight(1f)` 的语义正是「你最多吃掉剩余空间」：按钮那几格先按自身
        // 大小分走，剩下的才归面包屑，内容超出就在自己那一格里滚动。
        val crumbScroll = rememberScrollState()
        // 换目录后滚到末尾：当前目录是这一行最重要的信息，停在根上等于把
        // 「我在哪」藏在滚动区外面。跟着 maxValue 一起触发是因为首帧还没测量完，
        // 那时 maxValue 是 0，滚了也没用。
        LaunchedEffect(path, crumbScroll.maxValue) {
            crumbScroll.scrollTo(crumbScroll.maxValue)
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(crumbScroll),
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
        }
        // 目录缓存刻意不做自动刷新（在子目录待久了父目录可能已变，自动重取会把
        // 「秒回」变回「每次都等」），代价就是必须给用户一个手动的出口。
        // 放在面包屑行末尾：它是**当前目录**这一行的动作，与路径同处一行。
        IconButton(onClick = onStartSearch) {
            Icon(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = stringResource(R.string.serving_storage_search),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // 排在刷新**之前**：排序是「怎么看」，刷新是「重新取」，
        // 前者天天用、后者偶尔用。与浏览器文件面板同序。
        var sortExpanded by remember { mutableStateOf(false) }
        SortMenuAction(
            spec = sortSpec,
            timeLabel = R.string.serving_storage_sort_time,
            expanded = sortExpanded,
            onExpandedChange = { sortExpanded = it },
            onPick = onPickSort,
        )
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
    onPreview: (LocalEntry) -> Unit,
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
                mime = entry.mime,
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
                onClick = if (FilesListBuilder.isMedia(entry.mime)) {
                    { onPreview(entry) }
                } else {
                    null
                },
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
                else -> formatBytes(entry.size) + " · " + formatEntryDate(entry.mtime)
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

/** 存储行的测试标记。仪器测试用它拿到每行的真实边界，断言两两不重叠。 */
const val StorageRowTestTag = "storage-row"
