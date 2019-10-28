package ui

import kotlinx.coroutines.*
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Suppress("FunctionName")
class CoroutineExceptionHandlingTests {

    class FakePresenterForSingleExceptionHandling(val onSecondAction: () -> Unit) : BasePresenter() {

        var cancelledJobs = 0

        fun onCreate() {
            launch {
                delay(100)
                throw Error()
            }
            launch {
                delay(200)
                onSecondAction()
            }
        }
    }

    private val UI = newSingleThreadContext("UIThread") // Normally it will be Dispatchers.Main

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UI)
    }

    @Test
    fun `Error on a single coroutine, does not cancel others`() = runBlocking {
        var called = false
        val presenter = FakePresenterForSingleExceptionHandling(
                onSecondAction = { called = true }
        )
        presenter.onCreate()
        delay(300)
        assertTrue(called)
    }
}