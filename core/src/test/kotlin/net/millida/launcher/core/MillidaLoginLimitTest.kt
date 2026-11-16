package net.millida.launcher.core

import kotlinx.coroutines.runBlocking
import net.millida.launcher.core.account.LoginWaitWatch
import net.millida.launcher.core.account.MemoryVault
import net.millida.launcher.core.account.MillidaAuth
import net.millida.launcher.core.account.PendingLogin
import net.millida.launcher.core.account.RateLimited
import net.millida.launcher.core.account.RetryAfter
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * | вход                                              | вердикт                                              |
 * | ------------------------------------------------- | ---------------------------------------------------- |
 * | 429 с retryAfterSeconds в теле                    | RateLimited с этим числом секунд                     |
 * | 429 только с заголовком Retry-After               | RateLimited с числом из заголовка                    |
 * | 429 без срока, мусор, ноль, минус                 | RateLimited без срока: отсчёт не выдумывается        |
 * | опрос входа не достаёт сервер, игрок смотрит      | одно сообщение после трёх промахов подряд            |
 * | опрос не достаёт сервер, лаунчер за браузером     | молчание: сеть в фоне режет система                  |
 */
class MillidaLoginLimitTest {

    private lateinit var server: MockWebServer

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stop() {
        server.shutdown()
    }

    private fun auth() =
        MillidaAuth(
            OkHttpClient.Builder().retryOnConnectionFailure(false).build(),
            MemoryVault(),
            server.url("/v2").toString().trimEnd('/'),
        )

    private data class WaitCase(val body: String, val header: String?, val seconds: Int?, val why: String)

    private val waitCases = listOf(
        WaitCase("""{"statusCode":429,"message":"Слишком много писем","retryAfterSeconds":42}""", null, 42, "срок из тела — его и ждёт кнопка"),
        WaitCase("""{"retryAfterSeconds":"17"}""", null, 17, "число строкой — тот же срок"),
        WaitCase("""{"retryAfterSeconds":0.2}""", null, 1, "доля секунды округляется вверх: кнопка не должна открыться раньше сервера"),
        WaitCase("""{"retryAfterSeconds":42}""", "7", 42, "тело точнее заголовка: его пишет сам отказавший обработчик"),
        WaitCase("""{"message":"Слишком много попыток подряд"}""", "30", 30, "общий ограничитель отвечает сроком только в заголовке"),
        WaitCase("<html>429</html>", " 12 ", 12, "щит отдал HTML: остаётся заголовок"),
        WaitCase("""{"retryAfterSeconds":999999999}""", null, RetryAfter.MAX_SECONDS, "чужое число не запирает вход дольше суток"),
        WaitCase("""{"retryAfterSeconds":0}""", null, null, "ноль — не срок"),
        WaitCase("""{"retryAfterSeconds":-5}""", "-3", null, "отрицательное — не срок ни в теле, ни в заголовке"),
        WaitCase("""{"retryAfterSeconds":{"a":1}}""", null, null, "объект вместо числа не роняет вход"),
        WaitCase("""{"retryAfterSeconds":"скоро"}""", "Wed, 21 Oct 2026 07:28:00 GMT", null, "дата и слова — не секунды: отсчёт не выдумывается"),
        WaitCase("", null, null, "пустой ответ без заголовка — срока нет"),
    )

    @Test
    fun `срок ожидания читается из тела и заголовка 429`() {
        for (case in waitCases) {
            assertEquals(case.why, case.seconds, RetryAfter.seconds(case.body, case.header))
        }
    }

    @Test
    fun `отказ 429 на запросе кода входа несёт срок ожидания`() {
        for (case in waitCases) {
            val answer = MockResponse().setResponseCode(429).setBody(case.body)
            case.header?.let { answer.setHeader("Retry-After", it) }
            server.enqueue(answer)
            val error = runCatching { runBlocking { auth().startLogin(inline = true) } }.exceptionOrNull()
            assertTrue("429 — просьба подождать, а не безымянный сбой: ${case.why}; было $error", error is RateLimited)
            assertEquals("срок доходит до экрана входа: ${case.why}", case.seconds, (error as RateLimited).retryAfterSeconds)
        }
    }

    @Test
    fun `другие отказы сервера не выдаются за просьбу подождать`() {
        server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "30").setBody("""{"message":"обновляемся"}"""))
        val error = runCatching { runBlocking { auth().startLogin(inline = true) } }.exceptionOrNull()
        assertTrue("503 остаётся обычной ошибкой сервера; было $error", error is IOException && error !is RateLimited)
    }

    private data class Poll(val failed: Boolean, val visible: Boolean, val tell: Boolean)

    private data class WatchCase(val polls: List<Poll>, val why: String)

    @Test
    fun `о потерянной связи при ожидании входа говорится один раз и только на виду`() {
        val miss = Poll(failed = true, visible = true, tell = false)
        val hidden = Poll(failed = true, visible = false, tell = false)
        val answer = Poll(failed = false, visible = true, tell = false)
        val cases = listOf(
            WatchCase(listOf(miss, miss, miss.copy(tell = true), miss, miss), "три промаха подряд на виду — одно сообщение, дальше тишина"),
            WatchCase(listOf(miss, miss, answer, miss, miss), "ответ сервера между промахами обнуляет счёт: одиночный сбой не повод"),
            WatchCase(listOf(hidden, hidden, hidden, hidden), "игрок в браузере: сеть лаунчеру режет система, сообщение было бы ложным"),
            WatchCase(listOf(miss, miss, hidden, miss, miss, miss.copy(tell = true)), "уход в браузер обнуляет счёт: промахи до него не складываются с новыми"),
            WatchCase(
                listOf(miss, miss, miss.copy(tell = true), answer, miss, miss, miss.copy(tell = true)),
                "связь вернулась и пропала снова — это новая потеря, о ней говорится ещё раз",
            ),
        )
        for (case in cases) {
            val watch = LoginWaitWatch()
            case.polls.forEachIndexed { index, poll ->
                val failure = if (poll.failed) IOException("timeout") else null
                assertEquals("опрос ${index + 1}: ${case.why}", poll.tell, watch.shouldTell(failure, poll.visible))
            }
        }
    }

    @Test
    fun `ожидание входа сообщает о каждом опросе`() {
        server.enqueue(MockResponse().setResponseCode(502).setBody("<html>502</html>"))
        server.enqueue(MockResponse().setBody("""{"status":"pending"}"""))
        server.enqueue(MockResponse().setBody("""{"status":"ok","accessToken":"access-1"}"""))
        val heard = mutableListOf<Boolean>()
        val pending = PendingLogin("device-1", "ABCD-EFGH", "https://millida.net/auth/launcher", System.currentTimeMillis() + 60_000)
        val approved = runBlocking { auth().awaitApproval(pending, periodMs = 1) { failure -> heard += failure != null } }
        assertTrue("сбой опроса не обрывает ожидание", approved)
        assertEquals("без этих сигналов экран входа молчит, пока сервер недоступен", listOf(true, false, false), heard)
    }
}
