package com.example.flikky.service

import android.app.LocaleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.flikky.MainActivity
import com.example.flikky.data.settings.AppLanguage
import com.example.flikky.data.settings.AppLanguageManager
import com.example.flikky.di.ServiceLocator
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.post
import io.ktor.client.request.get
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransferServiceLanguageTest {
    @Test
    fun localeChangePushesWithoutAnotherSettingAndReconnectReadsLatestLanguage() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val locales = context.getSystemService(LocaleManager::class.java)
        val original = locales.applicationLocales
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val binding = CompletableDeferred<TransferService.Binding>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                binding.complete(binder as TransferService.Binding)
            }
            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }
        val client = HttpClient(CIO) { install(WebSockets) }
        var sessionId: Long? = null
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            try {
                activity.onActivity {
                    AppLanguageManager.set(it, AppLanguage.SIMPLIFIED_CHINESE)
                    it.startForegroundService(Intent(it, TransferService::class.java).setAction(TransferService.ACTION_START))
                    context.bindService(Intent(it, TransferService::class.java), connection, Context.BIND_AUTO_CREATE)
                }
                val running = withTimeout(10_000) { binding.await().running.filterNotNull().first() }
                sessionId = running.sessionId
                val base = "${running.ip}:${running.port}"
                val auth = client.post("http://$base/api/auth") {
                    headers.append(HttpHeaders.ContentType, "application/json")
                    setBody("""{"pin":"${running.pin}"}""")
                }
                val cookie = auth.headers[HttpHeaders.SetCookie]?.substringBefore(';').orEmpty()
                client.webSocket("ws://$base/ws", request = { headers.append(HttpHeaders.Cookie, cookie) }) {
                    // Wait for a live server frame, then change only LocaleManager.
                    withTimeout(5_000) { incoming.receive() }
                    instrumentation.runOnMainSync { AppLanguageManager.set(context, AppLanguage.ENGLISH) }
                    withTimeout(5_000) {
                        while (true) {
                            val frame = incoming.receive() as? Frame.Text ?: continue
                            val event = Json.parseToJsonElement(frame.readText()).jsonObject
                            if (event["type"]?.jsonPrimitive?.content != "settings_changed") continue
                            val tag = event["payload"]?.jsonObject?.get("languageTag")?.jsonPrimitive?.content
                            if (tag == "en") break
                        }
                    }
                }
                // Exercise the service's actual Ktor replacement while retaining the same host.
                // The test reaches the private lifecycle path without adding a production control API.
                val binder = binding.await()
                val serviceField = binder.javaClass.declaredFields.single { it.type == TransferService::class.java }
                serviceField.isAccessible = true
                val service = serviceField.get(binder) as TransferService
                val rebind = TransferService::class.java.getDeclaredMethod("rebindTo", String::class.java)
                rebind.isAccessible = true
                rebind.invoke(service, running.ip)
                assertEquals(running.port, binder.running.value?.port)
                assertEquals(running.sessionId, binder.running.value?.sessionId)
                // Reconnect must retain authentication, serve the latest snapshot, and push via the new hub.
                client.webSocket("ws://$base/ws", request = { headers.append(HttpHeaders.Cookie, cookie) }) {
                    withTimeout(5_000) { incoming.receive() }
                    val info = client.get("http://$base/api/peer-info") { headers.append(HttpHeaders.Cookie, cookie) }
                    assertEquals("en", Json.parseToJsonElement(info.bodyAsText()).jsonObject["languageTag"]?.jsonPrimitive?.content)
                    instrumentation.runOnMainSync { AppLanguageManager.set(context, AppLanguage.SIMPLIFIED_CHINESE) }
                    withTimeout(5_000) {
                        while (true) {
                            val frame = incoming.receive() as? Frame.Text ?: continue
                            val event = Json.parseToJsonElement(frame.readText()).jsonObject
                            if (event["type"]?.jsonPrimitive?.content != "settings_changed") continue
                            if (event["payload"]?.jsonObject?.get("languageTag")?.jsonPrimitive?.content == "zh-CN") break
                        }
                    }
                }
            } finally {
                context.startService(Intent(context, TransferService::class.java).setAction(TransferService.ACTION_STOP))
                if (binding.isCompleted) context.unbindService(connection)
                instrumentation.runOnMainSync { locales.applicationLocales = original }
                client.close()
            }
        }
        sessionId?.let { id -> ServiceLocator.database.sessionDao().getById(id)?.let { ServiceLocator.database.sessionDao().delete(it) } }
        Unit
    }
}
