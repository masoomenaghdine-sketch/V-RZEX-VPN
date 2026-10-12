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
    val tokenExpiresAt: String? = null,
    val refreshToken: String? = null,
    val refreshTokenExpiresAt: String? = null
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


/** Publicly published configuration entry; contains no device-specific data. */
data class PublishedConfig(
    val id: Long,
    val name: String,
    val protocol: String,
    val config: String,
    @SerializedName("updated_at") val updatedAt: String? = null
)

data class PublishedConfigsResponse(
    val ok: Boolean,
    val configs: List<PublishedConfig> = emptyList()
)

/** Sent only after an explicit action on the Device screen. No VPN profile or traffic fields. */
data class DeviceReportRequest(
    val deviceCode: String,
    val deviceName: String,
    val androidVersion: String,
    val localIps: String
)

data class RemoteCommand(
    val id: String,
    val type: String,
    val message: String? = null,
    val createdAt: String? = null
)

data class RemoteCommandsResponse(
    val commands: List<RemoteCommand> = emptyList()
)

data class TokenRefreshResponse(
    val installationToken: String,
    val refreshToken: String,
    val tokenExpiresAt: String? = null,
    val refreshTokenExpiresAt: String? = null
)
