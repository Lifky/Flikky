package com.example.flikky.ui.search

import android.app.Application
import app.cash.turbine.test
import com.example.flikky.data.SessionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * SearchViewModel 单测（v1.3 T16）。
 *
 * 不用 Robolectric 实例化真 Application —— `AndroidViewModel(app)` 只把 app 存起来，
 * 测试里用 `mockk<Application>(relaxed = true)` 足够，与 HistoryViewModelTest 风格一致。
 * 但 viewModelScope 内部依赖 Main dispatcher，所以仍要 `Dispatchers.setMain` 注入测试调度器，
 * 否则 stateIn 的协程跑不起来。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun empty_query_emits_empty_and_does_not_search() = runTest(dispatcher) {
        val repo = mockk<SessionRepository>()
        val vm = SearchViewModel(mockk<Application>(relaxed = true), repo)

        vm.results.test {
            // Wait past the debounce so stateIn settles on the initial empty value.
            advanceTimeBy(301L)
            assertEquals(emptyList<SessionRepository.SearchHit>(), expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { repo.search(any()) }
    }

    @Test fun query_change_triggers_search_after_debounce() = runTest(dispatcher) {
        val repo = mockk<SessionRepository>()
        val hit = SessionRepository.SearchHit(
            sessionId = 1L,
            sessionName = "s",
            messageId = 10L,
            snippet = "hello world",
            timestamp = 100L,
            kind = "TEXT",
        )
        coEvery { repo.search("hello") } returns listOf(hit)

        val vm = SearchViewModel(mockk<Application>(relaxed = true), repo)

        vm.results.test {
            // Initial empty emission. Don't advanceUntilIdle — that would race ahead
            // of debounce when we later send a query. Just advance enough for
            // stateIn to materialize the initial value (debounce 300ms + buffer).
            advanceTimeBy(301L)
            assertEquals(emptyList<SessionRepository.SearchHit>(), expectMostRecentItem())

            vm.onQueryChange("hello")
            advanceTimeBy(299L)
            // 还没到 300ms，不该有新 search 调用
            coVerify(exactly = 0) { repo.search(any()) }

            advanceTimeBy(2L)
            assertEquals(listOf(hit), expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 1) { repo.search("hello") }
    }

    @Test fun rapid_input_only_searches_for_last_query() = runTest(dispatcher) {
        val repo = mockk<SessionRepository>()
        coEvery { repo.search(any()) } returns emptyList()

        val vm = SearchViewModel(mockk<Application>(relaxed = true), repo)

        vm.results.test {
            advanceTimeBy(301L)
            expectMostRecentItem() // drain initial

            // 100ms 内连击三次，debounce 应只放最后一次过
            vm.onQueryChange("h")
            advanceTimeBy(50L)
            vm.onQueryChange("he")
            advanceTimeBy(50L)
            vm.onQueryChange("hel")
            advanceTimeBy(301L)

            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 1) { repo.search("hel") }
        coVerify(exactly = 0) { repo.search("h") }
        coVerify(exactly = 0) { repo.search("he") }
    }

    @Test fun whitespace_query_treated_as_empty() = runTest(dispatcher) {
        val repo = mockk<SessionRepository>()

        val vm = SearchViewModel(mockk<Application>(relaxed = true), repo)

        vm.results.test {
            advanceTimeBy(301L)
            expectMostRecentItem()  // drain initial empty

            vm.onQueryChange("   ")
            advanceTimeBy(400L)
            // trim → "" 与初始 "" 等同，StateFlow 的 distinct 语义不会重发；
            // 直接断言当前 value 仍是空列表，repo 也不应被调用。
            assertTrue(vm.results.value.isEmpty())

            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { repo.search(any()) }
    }
}
