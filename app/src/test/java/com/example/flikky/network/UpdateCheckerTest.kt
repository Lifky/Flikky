package com.example.flikky.network

import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateCheckerTest {

    private val sample = """
        {
          "tag_name": "v1.16.0",
          "name": "Flikky v1.16.0",
          "html_url": "https://github.com/Lifky/Flikky/releases/tag/v1.16.0",
          "body": "What's New\n- 文件总览\n- File overview",
          "prerelease": false
        }
    """.trimIndent()

    @Test
    fun parse_extracts_three_fields() {
        val info = UpdateChecker.parseLatestRelease(sample)
        assertEquals("v1.16.0", info?.tagName)
        assertEquals("https://github.com/Lifky/Flikky/releases/tag/v1.16.0", info?.htmlUrl)
        assertEquals("What's New\n- 文件总览\n- File overview", info?.body)
    }

    @Test
    fun parse_tolerates_missing_body() {
        val info = UpdateChecker.parseLatestRelease(
            """{"tag_name":"v1.2.3","html_url":"https://x"}""",
        )
        assertEquals("", info?.body)
    }

    @Test
    fun parse_rejects_garbage_and_missing_fields() {
        assertNull(UpdateChecker.parseLatestRelease("not json"))
        assertNull(UpdateChecker.parseLatestRelease("""{"message":"Not Found"}"""))
        assertNull(UpdateChecker.parseLatestRelease("""[1,2,3]"""))
    }

    @Test
    fun check_returns_null_on_fetch_failure() = runTest {
        val checker = UpdateChecker(fetchJson = { throw IOException("timeout") })
        assertNull(checker.check())
    }

    @Test
    fun check_returns_info_on_success() = runTest {
        val checker = UpdateChecker(fetchJson = { sample })
        assertEquals("v1.16.0", checker.check()?.tagName)
    }
}
