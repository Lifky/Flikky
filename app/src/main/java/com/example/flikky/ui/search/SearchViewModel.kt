package com.example.flikky.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.flikky.data.SessionRepository
import com.example.flikky.di.ServiceLocator
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 搜索屏 ViewModel（v1.3 T16）。
 *
 * 设计意图（D24 / D30 + 用户全局"反馈引导行动"）：
 * - 单一查询入口 [query]：UI 只管把用户输入塞进来，节流/触发查询由 Flow 链路负责，
 *   屏幕代码无须关心计时器、生命周期或线程切换。
 * - 300ms debounce：覆盖普通成人打字速度（≈250ms / 字），既避免逐字击库又保证
 *   "停顿即出结果"的体感连续性。短于 200ms 会浪费 IO，长于 500ms 让用户怀疑卡死。
 * - flatMapLatest：用户重新输入时立即取消上一次 search 协程，避免旧 query 的迟到
 *   结果覆盖新 query 的结果（典型竞态）。
 * - 空 query 直接发空列表：不会触发 repository.search，让"清空输入"即时回到初始态。
 * - WhileSubscribed(5000)：屏幕短暂不可见（如系统弹窗、配置变更）保持订阅，超过 5s
 *   再释放，避免重组抖动时的反复初始化。
 */
@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SearchViewModel(
    app: Application,
    private val repository: SessionRepository = ServiceLocator.repository,
) : AndroidViewModel(app) {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    val results: StateFlow<List<SessionRepository.SearchHit>> = _query
        .debounce(300L)
        .map { it.trim() }
        .flatMapLatest { q ->
            if (q.isEmpty()) flow { emit(emptyList()) }
            else flow { emit(repository.search(q)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    fun onQueryChange(q: String) {
        _query.value = q
    }

    companion object {
        fun factory(app: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer { SearchViewModel(app) }
        }
    }
}
