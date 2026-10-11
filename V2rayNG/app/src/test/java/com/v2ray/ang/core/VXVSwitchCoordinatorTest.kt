package com.v2ray.ang.core

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VXVSwitchCoordinatorTest {
    private class FakeEngines(
        var mainRunning: Boolean = false,
        var selectedGuid: String? = "profile-1",
        var mainStopSucceeds: Boolean = true,
        var vxvStartSucceeds: Boolean = true,
        var vxvStopSucceeds: Boolean = true,
        var mainStartSucceeds: Boolean = true,
    ) : VXVSwitchCoordinator.EngineControl {
        val calls = mutableListOf<String>()

        override suspend fun mainIsRunning() = mainRunning
        override suspend fun selectedMainProfileGuid() = selectedGuid

        override suspend fun stopMainAndAwaitReleased(timeoutMillis: Long): Boolean {
            calls += "stop-main"
            if (mainStopSucceeds) mainRunning = false
            return mainStopSucceeds
        }

        override suspend fun startVXVAndAwaitConnected(timeoutMillis: Long): Boolean {
            calls += "start-vxv"
            return vxvStartSucceeds
        }

        override suspend fun stopVXVAndAwaitReleased(timeoutMillis: Long): Boolean {
            calls += "stop-vxv"
            return vxvStopSucceeds
        }

        override suspend fun startMainProfile(profileGuid: String): Boolean {
            calls += "start-main:$profileGuid"
            if (mainStartSucceeds) mainRunning = true
            return mainStartSucceeds
        }
    }

    @Test
    fun stoppedMainRemainsStoppedAfterReturningFromVXV() = runBlocking {
        val engines = FakeEngines(mainRunning = false)
        val coordinator = VXVSwitchCoordinator(engines)

        assertEquals(
            VXVSwitchCoordinator.Phase.VXV_ACTIVE,
            coordinator.enterVXV().phase,
        )
        assertEquals(
            VXVSwitchCoordinator.Phase.IDLE,
            coordinator.returnToMain().phase,
        )
        assertFalse(engines.calls.contains("start-main:profile-1"))
        assertEquals(listOf("start-vxv", "stop-vxv"), engines.calls)
    }

    @Test
    fun mainMustBeReleasedBeforePsiphonStarts() = runBlocking {
        val engines = FakeEngines(mainRunning = true)
        val coordinator = VXVSwitchCoordinator(engines)

        val result = coordinator.enterVXV()

        assertEquals(VXVSwitchCoordinator.Phase.VXV_ACTIVE, result.phase)
        assertTrue(engines.calls.indexOf("stop-main") < engines.calls.indexOf("start-vxv"))
    }

    @Test
    fun mainStopTimeoutPreventsVXVStart() = runBlocking {
        val engines = FakeEngines(mainRunning = true, mainStopSucceeds = false)
        val coordinator = VXVSwitchCoordinator(engines)

        val result = coordinator.enterVXV()

        assertEquals(VXVSwitchCoordinator.Phase.FAILED, result.phase)
        assertFalse(engines.calls.contains("start-vxv"))
    }

    @Test
    fun failedPsiphonStartRestoresPreviouslyRunningMainProfile() = runBlocking {
        val engines = FakeEngines(mainRunning = true, vxvStartSucceeds = false)
        val coordinator = VXVSwitchCoordinator(engines)

        val result = coordinator.enterVXV()

        assertEquals(VXVSwitchCoordinator.Phase.FAILED, result.phase)
        assertTrue(engines.calls.contains("start-main:profile-1"))
    }

    @Test
    fun mainDoesNotStartIfPsiphonStopCannotBeConfirmed() = runBlocking {
        val engines = FakeEngines(mainRunning = true, vxvStopSucceeds = false)
        val coordinator = VXVSwitchCoordinator(engines)
        coordinator.enterVXV()

        val result = coordinator.returnToMain()

        assertEquals(VXVSwitchCoordinator.Phase.FAILED, result.phase)
        assertFalse(engines.calls.contains("start-main:profile-1"))
    }

    @Test
    fun previousMainProfileIsRestoredOnlyAfterPsiphonStops() = runBlocking {
        val engines = FakeEngines(mainRunning = true)
        val coordinator = VXVSwitchCoordinator(engines)
        coordinator.enterVXV()

        coordinator.returnToMain()

        assertTrue(engines.calls.indexOf("stop-vxv") < engines.calls.indexOf("start-main:profile-1"))
    }
}
