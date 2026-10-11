package com.v2ray.ang.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes ownership handover between the main Xray stack and the VXV/Psiphon stack.
 *
 * This coordinator deliberately depends on an engine-control interface: Android service state,
 * VPN-interface release, and Psiphon connection state must be supplied by the real service
 * integration rather than guessed by a UI screen.
 */
class VXVSwitchCoordinator(
    private val engines: EngineControl,
    private val onStateChanged: (State) -> Unit = {},
) {
    enum class ActiveEnvironment { MAIN, VXV }
    enum class Phase {
        IDLE,
        STOPPING_MAIN,
        STARTING_VXV,
        VXV_ACTIVE,
        STOPPING_VXV,
        RESTORING_MAIN,
        FAILED,
    }

    data class State(
        val phase: Phase = Phase.IDLE,
        val activeEnvironment: ActiveEnvironment = ActiveEnvironment.MAIN,
        val mainWasRunningBeforeVXV: Boolean = false,
        val savedMainProfileGuid: String? = null,
        val error: String? = null,
    )

    interface EngineControl {
        /** Snapshot of service-owned state, not just the UI's selected profile. */
        suspend fun mainIsRunning(): Boolean
        suspend fun selectedMainProfileGuid(): String?
        /** Must return true only after the main VPN interface is released. */
        suspend fun stopMainAndAwaitReleased(timeoutMillis: Long): Boolean
        /** Must return true only after Psiphon reports a real connected state. */
        suspend fun startVXVAndAwaitConnected(timeoutMillis: Long): Boolean
        /** Must return true only after Psiphon has released its VPN interface/resources. */
        suspend fun stopVXVAndAwaitReleased(timeoutMillis: Long): Boolean
        /** Starts exactly the captured main profile and returns whether start was accepted. */
        suspend fun startMainProfile(profileGuid: String): Boolean
    }

    private val mutex = Mutex()

    @Volatile
    var state: State = State()
        private set

    suspend fun enterVXV(
        stopTimeoutMillis: Long = DEFAULT_STOP_TIMEOUT_MS,
        startTimeoutMillis: Long = DEFAULT_START_TIMEOUT_MS,
    ): State = mutex.withLock {
        if (state.phase != Phase.IDLE && state.phase != Phase.FAILED) return@withLock state
        val wasRunning = engines.mainIsRunning()
        val profileGuid = engines.selectedMainProfileGuid()
        publish(
            State(
                phase = if (wasRunning) Phase.STOPPING_MAIN else Phase.STARTING_VXV,
                activeEnvironment = ActiveEnvironment.MAIN,
                mainWasRunningBeforeVXV = wasRunning,
                savedMainProfileGuid = profileGuid,
            )
        )

        if (wasRunning && !engines.stopMainAndAwaitReleased(stopTimeoutMillis)) {
            return@withLock fail("Main VPN did not confirm tunnel release")
        }

        publish(state.copy(phase = Phase.STARTING_VXV, error = null))
        if (engines.startVXVAndAwaitConnected(startTimeoutMillis)) {
            publish(state.copy(phase = Phase.VXV_ACTIVE, activeEnvironment = ActiveEnvironment.VXV))
            return@withLock state
        }

        // A failed VXV start must not leave the user stranded if the main tunnel was active.
        if (wasRunning && !profileGuid.isNullOrBlank()) {
            publish(state.copy(phase = Phase.RESTORING_MAIN))
            if (engines.startMainProfile(profileGuid)) {
                return@withLock fail("Psiphon did not connect; previous main profile was restored")
            }
        }
        fail("Psiphon did not confirm a connected state")
    }

    suspend fun returnToMain(
        stopTimeoutMillis: Long = DEFAULT_STOP_TIMEOUT_MS,
    ): State = mutex.withLock {
        if (state.phase != Phase.VXV_ACTIVE && state.phase != Phase.FAILED) return@withLock state
        val snapshot = state
        publish(snapshot.copy(phase = Phase.STOPPING_VXV, error = null))

        if (!engines.stopVXVAndAwaitReleased(stopTimeoutMillis)) {
            return@withLock fail("Psiphon did not confirm tunnel release; Xray was not started")
        }

        if (snapshot.mainWasRunningBeforeVXV) {
            val guid = snapshot.savedMainProfileGuid
            if (guid.isNullOrBlank()) return@withLock fail("Previous main profile is unavailable")
            publish(snapshot.copy(phase = Phase.RESTORING_MAIN, error = null))
            if (!engines.startMainProfile(guid)) {
                return@withLock fail("Previous main profile could not be restored")
            }
        }

        publish(State(phase = Phase.IDLE, activeEnvironment = ActiveEnvironment.MAIN))
        state
    }

    private fun fail(message: String): State {
        val failed = state.copy(phase = Phase.FAILED, error = message)
        publish(failed)
        return failed
    }

    private fun publish(next: State) {
        state = next
        onStateChanged(next)
    }

    companion object {
        const val DEFAULT_STOP_TIMEOUT_MS = 8_000L
        const val DEFAULT_START_TIMEOUT_MS = 45_000L
    }
}
