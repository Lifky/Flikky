package com.example.flikky.ui.home

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.flikky.R
import com.example.flikky.data.SessionRepository
import com.example.flikky.data.db.entities.SessionEntity
import com.example.flikky.ui.search.SearchViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 主页顶部 SearchBar：折叠态取代顶栏（去标题），trailing overflow 收纳「导入」。
 * 原地展开为全屏搜索，结果分「会话」（名称匹配）+「消息」（FTS，复用 SearchViewModel）两组。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeSearchBar(
    sessions: List<SessionEntity>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onOpenSession: (Long) -> Unit,
    onResume: () -> Unit,
    onOpenMessageHit: (sessionId: Long, messageId: Long) -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val searchVm: SearchViewModel = viewModel(
        factory = SearchViewModel.factory(ctx.applicationContext as Application),
    )
    val query by searchVm.query.collectAsState()
    // settledQuery is debounced+trimmed — both result groups derive from it so they move in lockstep.
    val settledQuery by searchVm.debouncedQuery.collectAsState()
    val msgHits by searchVm.results.collectAsState()
    // expanded 受控（上提到 HomeScreen）：用于隐藏 FAB/底栏并让主页铺满全屏。
    // Intentionally driven by settledQuery (not raw query) so session hits and message hits
    // update simultaneously — prevents one group leading the other by ~300 ms.
    val sessionHits = remember(sessions, settledQuery) { matchSessionsByName(sessions, settledQuery) }
    val settled = settledQuery == query.trim()

    fun collapse() {
        onExpandedChange(false)
        searchVm.onQueryChange("")
    }

    SearchBar(
        modifier = modifier.fillMaxWidth(),
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        inputField = {
            SearchBarDefaults.InputField(
                query = query,
                onQueryChange = searchVm::onQueryChange,
                onSearch = { },
                expanded = expanded,
                onExpandedChange = onExpandedChange,
                placeholder = { Text("搜索会话与消息") },
                leadingIcon = {
                    if (expanded) {
                        IconButton(onClick = { collapse() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "收起搜索")
                        }
                    } else {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    }
                },
                trailingIcon = {
                    if (expanded) {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { searchVm.onQueryChange("") }) {
                                Icon(Icons.Default.Close, contentDescription = "清空")
                            }
                        }
                    } else {
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menu = true }) {
                                Icon(painterResource(R.drawable.ic_more_vert), contentDescription = "更多")
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text("导入") },
                                    onClick = { menu = false; onImport() },
                                    leadingIcon = {
                                        Icon(painterResource(R.drawable.ic_file_download), contentDescription = null)
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) {
        when {
            query.isBlank() -> CenterHint("输入关键词搜索会话与消息")
            sessionHits.isEmpty() && msgHits.isEmpty() ->
                // Only show "no match" once the debounce has settled; suppress the ~300ms flash
                // that would otherwise appear when a query has only message hits (not session-name hits).
                if (settled) CenterHint("没有找到匹配项") else Box(Modifier.fillMaxSize())
            // 全屏展开后内容会延伸到导航栏/键盘之下，给结果列表补 nav + ime inset，末项不被遮挡。
            else -> LazyColumn(modifier = Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
                if (sessionHits.isNotEmpty()) {
                    item { SectionHeader("会话") }
                    items(sessionHits, key = { "s${it.id}" }) { s ->
                        SessionHitRow(s) {
                            collapse()
                            if (s.endedAt == null) onResume() else onOpenSession(s.id)
                        }
                    }
                }
                if (msgHits.isNotEmpty()) {
                    item { SectionHeader("消息") }
                    items(msgHits, key = { "m${it.messageId}" }) { hit ->
                        MessageHitRow(hit) { collapse(); onOpenMessageHit(hit.sessionId, hit.messageId) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .semantics { heading() }
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun CenterHint(text: String) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SessionHitRow(s: SessionEntity, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            s.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (s.endedAt == null) "进行中" else s.previewText ?: "${s.messageCount} 条消息 · ${s.fileCount} 文件",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MessageHitRow(
    hit: SessionRepository.SearchHit,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            hit.sessionName,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (hit.kind == "FILE") {
                Icon(
                    // 与消息文件气泡（MessageBubble 用 ic_description）统一图标风格。
                    painterResource(R.drawable.ic_description),
                    contentDescription = "文件",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(4.dp))
            }
            Text(
                hit.snippet,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(hit.timestamp)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
