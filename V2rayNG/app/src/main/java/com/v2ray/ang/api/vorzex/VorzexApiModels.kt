package com.v2ray.ang.api.vorzex

import com.google.gson.annotations.SerializedName

/**
 * Wire contracts shared by the Android client and the separately deployed VØRZEX backend.
 *
 * Keep these DTOs free of raw VPN configs, subscription URLs, credentials, and private keys.
 */
data class InstallationRegisterRequest(
    val installationId: String,
    val appVersion: String,
    val consentedAt: String
)

data class InstallationRegisterResponse(
    val installationId: String,
    val installationToken: String,
    val tokenExpiresAt: String? = null
)

enum class ClientEventType(val wireValue: String) {
    @SerializedName("app_started") APP_STARTED("app_started"),
    @SerializedName("vpn_connecting") VPN_CONNECTING("vpn_connecting"),
    @SerializedName("vpn_connected") VPN_CONNECTED("vpn_connected"),
    @SerializedName("vpn_disconnected") VPN_DISCONNECTED("vpn_disconnected"),
    @SerializedName("heartbeat") HEARTBEAT("heartbeat")
}

/**
 * The server treats these as client-reported observations, not authoritative proof
 * of a remote server session.
 */
data class ClientEventRequest(
    val eventId: String,
    val installationId: String,
    val eventType: ClientEventType,
    val occurredAt: String,
    val appVersion: String,
    val profileLabel: String? = null,
    val profileSource: String? = null
)

data class ClientAccessStatusResponse(
    val allowed: Boolean,
    val reasonCode: String? = null,
    val message: String? = null,
    val recheckAfterSeconds: Long = 300
)

data class ApiErrorResponse(
    val error: String? = null,
    val message: String? = null,
    val requestId: String? = null
)
