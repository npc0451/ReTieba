package dev.infinityf4p.tiebapure

import dev.infinityf4p.tiebapure.core.media.MediaCacheFiles
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderCacheMaintenanceTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun repeatedRequestsWaitForIdleAndRespectTheMinimumInterval() = runTest {
        val runs = mutableListOf<Long>()
        val cleaner = ReaderCacheMaintenance(backgroundScope, nowMilliseconds = { testScheduler.currentTime }) { runs += testScheduler.currentTime }
        repeat(20) { cleaner.request() }
        runCurrent(); advanceTimeBy(4_999); runCurrent(); assertTrue(runs.isEmpty())
        advanceTimeBy(1); runCurrent(); assertEquals(listOf(5_000L), runs)
        cleaner.request(); runCurrent(); advanceTimeBy(10_000); runCurrent(); assertEquals(1, runs.size)
        advanceTimeBy(50_000); runCurrent(); assertEquals(listOf(5_000L, 65_000L), runs)
    }

    @Test fun ordinaryCleanupWaitsUntilTheTouchGestureHasEnded() = runTest {
        var runs = 0
        val cleaner = ReaderCacheMaintenance(backgroundScope, nowMilliseconds = { testScheduler.currentTime }) { runs++ }
        cleaner.request(); cleaner.interaction(true)
        runCurrent(); advanceTimeBy(120_000); runCurrent(); assertEquals(0, runs)
        cleaner.interaction(false); runCurrent()
        advanceTimeBy(4_999); runCurrent(); assertEquals(0, runs)
        advanceTimeBy(1); runCurrent(); assertEquals(1, runs)
    }

    @Test fun pressureBypassesIdleAndThrottlingAndIsNotLostInAnOrdinaryRequest() = runTest {
        val runs = mutableListOf<Boolean>()
        val cleaner = ReaderCacheMaintenance(backgroundScope, nowMilliseconds = { testScheduler.currentTime }) { runs += it }
        cleaner.request(); runCurrent(); advanceTimeBy(5_000); runCurrent()
        cleaner.request(storagePressure = true); cleaner.request(); runCurrent()
        assertEquals(listOf(false, true), runs)
    }

    @Test fun finalReaderReleaseSchedulesARecheckButDoubleReleaseDoesNot() = runTest {
        var runs = 0
        ReaderCacheMaintenance(backgroundScope, nowMilliseconds = { testScheduler.currentTime }) { runs++ }
        runCurrent()
        val file = temp.newFile("protected")
        val first = MediaCacheFiles.protect(file)
        val second = MediaCacheFiles.protect(file)
        first.close(); runCurrent(); advanceTimeBy(5_000); runCurrent(); assertEquals(0, runs)
        second.close(); second.close(); runCurrent(); advanceTimeBy(5_000); runCurrent()
        assertEquals(1, runs)
    }

    @Test fun failedCleanupDoesNotKillFutureRequests() = runTest {
        var attempts = 0
        val cleaner = ReaderCacheMaintenance(backgroundScope, nowMilliseconds = { testScheduler.currentTime }) {
            attempts++
            if (attempts == 1) throw java.io.IOException("test failure")
        }
        cleaner.request(); runCurrent(); advanceTimeBy(5_000); runCurrent()
        cleaner.request(); runCurrent(); advanceTimeBy(60_000); runCurrent()
        assertEquals(2, attempts)
    }

    @Test fun pressureFailureCannotCauseRepeatedImmediateRetries() = runTest {
        var attempts = 0
        val cleaner = ReaderCacheMaintenance(backgroundScope, nowMilliseconds = { testScheduler.currentTime }) {
            attempts++; throw java.io.IOException("test pressure failure")
        }
        cleaner.request(storagePressure = true); runCurrent(); assertEquals(1, attempts)
        repeat(3) { cleaner.request(); runCurrent() }
        assertEquals(1, attempts)
        advanceTimeBy(5_000); runCurrent(); assertEquals(2, attempts)
        advanceTimeBy(60_000); runCurrent(); assertEquals(2, attempts)
    }

    @Test fun interruptedCleanupNeverOverlapsAndPressureIsRetried() = runTest {
        var active = 0
        var maximumActive = 0
        val pressureRuns = mutableListOf<Boolean>()
        val cleaner = ReaderCacheMaintenance(backgroundScope, nowMilliseconds = { testScheduler.currentTime }) { pressure ->
            active++; maximumActive = maxOf(maximumActive, active)
            try {
                pressureRuns += pressure
                if (pressureRuns.size == 1) awaitCancellation()
            } finally { active-- }
        }
        cleaner.request(storagePressure = true); runCurrent()
        cleaner.request(); runCurrent(); advanceTimeBy(5_000); runCurrent()
        assertEquals(listOf(true, true), pressureRuns)
        assertEquals(1, maximumActive)
        assertEquals(0, active)
    }
}
