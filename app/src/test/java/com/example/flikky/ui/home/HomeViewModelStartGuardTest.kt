package com.example.flikky.ui.home

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.flikky.data.SessionRepository
import com.example.flikky.data.db.entities.SessionEntity
import com.example.flikky.data.settings.FlikkySettings
import com.example.flikky.data.settings.SettingsRepository
import com.example.flikky.session.SessionState
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 没有可用 Wi-Fi 地址时绝不能启动服务：服务会在前台通知闪一下之后自杀，而 UI 已经跳进传输页，
 * 留在那里对着空 URL 转圈（v1.17.0 装机反馈）。守在 ViewModel，UI 只负责把拒绝原因翻成文案。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HomeViewModelStartGuardTest {

    private lateinit var app: Application
    private lateinit var repo: SessionRepository
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var session: SessionState
    private var wifiIp: String? = null

    @Before fun setUp() {
        app = spyk(ApplicationProvider.getApplicationContext())
        every { app.startForegroundService(any()) } returns null

        repo = mockk()
        every { repo.observeSessions() } returns MutableStateFlow(emptyList<SessionEntity>())
        every { repo.observeGroups() } returns MutableStateFlow(emptyList())
        settingsRepo = mockk()
        every { settingsRepo.settings } returns MutableStateFlow(FlikkySettings())
        session = SessionState(nowMs = { 10_000L })
    }

    private fun viewModel() = HomeViewModel(
        app = app,
        repository = repo,
        sessionState = session,
        settingsRepository = settingsRepo,
        currentWifiIp = { wifiIp },
    )

    @Test
    fun startsWhenWifiHasAUsableAddress() {
        wifiIp = "192.168.1.7"

        assertEquals(HomeViewModel.StartResult.Success, viewModel().startService())

        verify(exactly = 1) { app.startForegroundService(any()) }
    }

    @Test
    fun refusesWithoutWifi() {
        wifiIp = null

        assertEquals(HomeViewModel.StartResult.NoUsableNetwork, viewModel().startService())

        verify(exactly = 0) { app.startForegroundService(any()) }
    }

    /** 自配置地址绑得上却连不通，等同于没有网络。 */
    @Test
    fun refusesLinkLocalAddress() {
        wifiIp = "169.254.7.7"

        assertEquals(HomeViewModel.StartResult.NoUsableNetwork, viewModel().startService())

        verify(exactly = 0) { app.startForegroundService(any()) }
    }
}
