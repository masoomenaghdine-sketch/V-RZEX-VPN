package com.v2ray.ang.api.vorzex

import com.google.gson.annotations.SerializedName

/** Publicly published configuration entry. No device-specific data is part of this model. */
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

data class ApiErrorResponse(
    val error: String? = null,
    val message: String? = null,
    val requestId: String? = null
)
