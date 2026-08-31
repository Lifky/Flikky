package com.example.flikky.ui.serving.storage

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.flikky.R
import com.example.flikky.ui.components.FileLeadingSpec
import com.example.flikky.ui.components.FileLeadingVisual
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
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            StorageBreadcrumb(path = state.path, onNavigate = onOpenDir)
            // 加载中：画一条线性进度。大目录列举要几百毫秒到几秒，没有它用户点了
            // 完全看不出有反应（装机验收）。面包屑已经在上面先动了，这里补「正在做事」。
            if (state.loading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.screenEdge, vertical = Spacing.xl),
                )
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
                    modifier = Modifier.fillMaxSize(),
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
                        // 行的增删移动走官方 item 动画（换目录、勾选重排时不再硬切）。
                        // 逐行 stagger 在几千行的目录里会拖成一场幻灯片，所以只用
                        // animateItem 的默认位移/淡入，不自己叠延迟。
                        Box(modifier = Modifier.animateItem()) {
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
                }
            }
        }
        // 官方 MD3 FAB 菜单，右下角。**不是** floating toolbar：那个组件的 content
        // 契约是「一串 IconButton」，塞进选中计数这类自由文本会把容器撑成一个
        // 巨型椭圆（装机验收 Screenshot_4）。计数改放进菜单项文案，见 StorageSelectionFab。
        StorageSelectionFab(
            summary = summary,
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
private fun StorageBreadcrumb(path: String, onNavigate: (String) -> Unit) {
    // 路径变化时整条淡入淡出 + 轻微横移，方向随「进/退」而定：
    // 进目录时新面包屑从右滑入，返回时从左滑入。这一条是「不硬」的主要来源，
    // 因为面包屑是点击后第一个变化的东西（列表还在加载）。
    // spec 必须在 composable 体内先取：`transitionSpec` 不是 @Composable
    // （与 ServingScreen 的连接头动画、NavTransitions 同一套路），
    // 在里面调 Motion.xxx() 会编译不过。先取后闭包捕获。
    val slideSpec = Motion.spatialFast<IntOffset>()
    val enterFade = Motion.effects<Float>()
    val exitFade = Motion.effectsFast<Float>()
    AnimatedContent(
        targetState = path,
        transitionSpec = {
            val forward = targetState.length > initialState.length
            val shift = if (forward) 1 else -1
            (slideInHorizontally(slideSpec) { it / 6 * shift } +
                fadeIn(enterFade)) togetherWith
                (slideOutHorizontally(slideSpec) { -it / 6 * shift } +
                    fadeOut(exitFade))
        },
        label = "StorageCrumbs",
    ) { shownPath ->
        StorageBreadcrumbRow(shownPath, onNavigate)
    }
}

@Composable
private fun StorageBreadcrumbRow(path: String, onNavigate: (String) -> Unit) {
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
                entry.isDir -> stringResource(R.string.serving_storage_folder)
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
