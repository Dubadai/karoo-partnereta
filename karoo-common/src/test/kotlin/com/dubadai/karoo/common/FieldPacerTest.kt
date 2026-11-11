package com.dubadai.karoo.common

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FieldPacerTest {
    private val state = MutableStateFlow(1)
    private val page = MutableStateFlow<String?>("A")

    @Test fun `sleeps the whole time when nothing changes`() = runTest {
        val pace = FieldPacer(listOf(state, page))
        pace.mark()
        pace.sleep(800)
        assertEquals(800, currentTime)
    }

    @Test fun `wakes when a flow changes`() = runTest {
        val pace = FieldPacer(listOf(state, page))
        pace.mark()
        launch { delay(300); state.value = 2 }
        pace.sleep(900)
        assertEquals(300, currentTime)
    }

    @Test fun `wakes on the second flow too`() = runTest {
        val pace = FieldPacer(listOf(state, page))
        pace.mark()
        launch { delay(250); page.value = "B" }
        pace.sleep(900)
        assertEquals(250, currentTime)
    }

    @Test fun `a change between mark and sleep returns at once`() = runTest {
        val pace = FieldPacer(listOf(state, page))
        pace.mark()
        state.value = 5
        pace.sleep(900)
        assertEquals(0, currentTime)
    }

    @Test fun `never sleeps longer than the cap`() = runTest {
        val pace = FieldPacer(listOf(state), capMs = 1_000)
        pace.mark()
        pace.sleep(5_000)
        assertEquals(1_000, currentTime)
    }

    @Test fun `zero or less returns at once`() = runTest {
        val pace = FieldPacer(listOf(state))
        pace.sleep(0)
        pace.sleep(-40)
        assertEquals(0, currentTime)
    }

    @Test fun `next sleep ends the gap when a change waits`() {
        assertEquals(620, FieldPacer.nextMs(pending = true, sinceMs = 300, minGapMs = 920, idleMs = 5_000))
    }

    @Test fun `next sleep runs to the idle redraw otherwise`() {
        assertEquals(4_700, FieldPacer.nextMs(pending = false, sinceMs = 300, minGapMs = 920, idleMs = 5_000))
    }
}
