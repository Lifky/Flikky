package com.example.flikky.service

import com.example.flikky.data.settings.FlikkySettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * peer-info 只向浏览器声明**真的能看**的通道（D76）：前置条件与对端开关两轴都开。
 *
 * 只看对端开关的后果：未授权时浏览器照样渲染「文件」「相册」入口，面板里是
 * 「需要在手机上授权」的空壳，并开始轮询 —— 用户一授权，内容自己就出来了。
 * 收藏同理：只看功能开关时，对端开关关着浏览器也显示收藏入口。
 */
class PeerInfoChannelGatingTest {

    private val allOn = FlikkySettings(
        storageBrowsingEnabled = true,
        albumBrowsingEnabled = true,
        favoriteBetaEnabled = true,
        favoriteBrowsingEnabled = true,
    )

    private fun FlikkySettings.dto(storageAvailable: Boolean, albumAvailable: Boolean) =
        with(TransferService.Companion) {
            toPeerInfoDto(
                systemDark = false,
                defaultDeviceName = "Phone",
                storageAvailable = storageAvailable,
                albumAvailable = albumAvailable,
            )
        }

    @Test
    fun `every channel is declared when both axes are open`() {
        val dto = allOn.dto(storageAvailable = true, albumAvailable = true)
        assertTrue(dto.storageBrowsingEnabled)
        assertTrue(dto.albumBrowsingEnabled)
        assertTrue(dto.favoriteEnabled)
    }

    @Test
    fun `a gate left open without storage permission is not declared`() {
        val dto = allOn.dto(storageAvailable = false, albumAvailable = true)
        assertFalse(dto.storageBrowsingEnabled)
        assertTrue(dto.albumBrowsingEnabled)
    }

    @Test
    fun `a gate left open without photo access is not declared`() {
        val dto = allOn.dto(storageAvailable = true, albumAvailable = false)
        assertFalse(dto.albumBrowsingEnabled)
        assertTrue(dto.storageBrowsingEnabled)
    }

    @Test
    fun `favourites need the peer gate, not just the app feature`() {
        val dto = allOn.copy(favoriteBrowsingEnabled = false)
            .dto(storageAvailable = true, albumAvailable = true)
        assertFalse(dto.favoriteEnabled)
    }

    @Test
    fun `favourites need the app feature, not just the peer gate`() {
        val dto = allOn.copy(favoriteBetaEnabled = false)
            .dto(storageAvailable = true, albumAvailable = true)
        assertFalse(dto.favoriteEnabled)
    }

    @Test
    fun `an unspecified prerequisite defaults to closed`() {
        val dto = with(TransferService.Companion) {
            allOn.toPeerInfoDto(systemDark = false, defaultDeviceName = "Phone")
        }
        assertFalse(dto.storageBrowsingEnabled)
        assertFalse(dto.albumBrowsingEnabled)
    }

    @Test
    fun `peer favoriting is declared only with the favourites feature on`() {
        val on = allOn.copy(allowPeerFavorite = true)
        assertTrue(on.dto(storageAvailable = true, albumAvailable = true).allowPeerFavorite)
        assertFalse(
            on.copy(favoriteBetaEnabled = false).dto(storageAvailable = true, albumAvailable = true).allowPeerFavorite,
        )
        assertFalse(allOn.dto(storageAvailable = true, albumAvailable = true).allowPeerFavorite)
    }
}
