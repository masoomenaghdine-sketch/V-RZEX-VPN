package com.v2ray.ang.util

import org.json.JSONArray
import org.json.JSONObject

/**
 * Versioned, text-based VØRZEX profile exchange envelope.
 *
 * The .vzv suffix identifies this envelope; it is not a claim that an arbitrary
 * NPV/NPVS payload can be decoded. NPV formats must be parsed by a verified,
 * format-specific adapter before they can be converted to Xray profiles.
 */
object VorzexConfigExchange {
    const val FILE_EXTENSION = "vxv"
    const val MIME_TYPE = "application/vnd.vorzex.config+json"
    private const val FORMAT = "VORZEX_CONFIG_EXCHANGE"
    private const val CURRENT_VERSION = 1
    private const val MAX_DOCUMENT_CHARS = 2_000_000
    private const val MAX_PROFILES = 500
    private const val MAX_PROFILE_CHARS = 256_000

    data class ProfilePayload(
        val name: String,
        val type: String,
        val content: String,
    )

    sealed interface DecodeResult {
        data class Success(val profiles: List<ProfilePayload>) : DecodeResult
        data class Failure(val reason: Reason) : DecodeResult
    }

    enum class Reason {
        EMPTY_DOCUMENT,
        DOCUMENT_TOO_LARGE,
        INVALID_JSON,
        WRONG_FORMAT,
        UNSUPPORTED_VERSION,
        EMPTY_PROFILES,
        TOO_MANY_PROFILES,
        INVALID_PROFILE,
        PROFILE_TOO_LARGE,
    }

    fun encode(profiles: List<ProfilePayload>): String {
        require(profiles.isNotEmpty()) { "At least one profile is required" }
        require(profiles.size <= MAX_PROFILES) { "Too many profiles" }
        profiles.forEach {
            require(it.name.isNotBlank() && it.type.isNotBlank() && it.content.isNotBlank()) {
                "Profile name, type and content are required"
            }
            require(it.content.length <= MAX_PROFILE_CHARS) { "Profile is too large" }
        }

        val profileArray = JSONArray()
        profiles.forEach { profile ->
            profileArray.put(
                JSONObject()
                    .put("name", profile.name)
                    .put("type", profile.type)
                    .put("content", profile.content)
            )
        }
        return JSONObject()
            .put("format", FORMAT)
            .put("version", CURRENT_VERSION)
            .put("profiles", profileArray)
            .toString(2)
    }

    fun decode(document: String): DecodeResult {
        if (document.isBlank()) return DecodeResult.Failure(Reason.EMPTY_DOCUMENT)
        if (document.length > MAX_DOCUMENT_CHARS) {
            return DecodeResult.Failure(Reason.DOCUMENT_TOO_LARGE)
        }

        val root = try {
            JSONObject(document)
        } catch (_: Exception) {
            return DecodeResult.Failure(Reason.INVALID_JSON)
        }
        if (root.optString("format") != FORMAT) {
            return DecodeResult.Failure(Reason.WRONG_FORMAT)
        }
        if (root.optInt("version", -1) != CURRENT_VERSION) {
            return DecodeResult.Failure(Reason.UNSUPPORTED_VERSION)
        }

        val array = root.optJSONArray("profiles")
            ?: return DecodeResult.Failure(Reason.EMPTY_PROFILES)
        if (array.length() == 0) return DecodeResult.Failure(Reason.EMPTY_PROFILES)
        if (array.length() > MAX_PROFILES) return DecodeResult.Failure(Reason.TOO_MANY_PROFILES)

        val profiles = ArrayList<ProfilePayload>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index)
                ?: return DecodeResult.Failure(Reason.INVALID_PROFILE)
            val name = item.optString("name")
            val type = item.optString("type")
            val content = item.optString("content")
            if (name.isBlank() || type.isBlank() || content.isBlank()) {
                return DecodeResult.Failure(Reason.INVALID_PROFILE)
            }
            if (content.length > MAX_PROFILE_CHARS) {
                return DecodeResult.Failure(Reason.PROFILE_TOO_LARGE)
            }
            profiles += ProfilePayload(name, type, content)
        }
        return DecodeResult.Success(profiles)
    }

    /**
     * Do not treat these formats as interchangeable. A decoder for the real NPV
     * binary/container format must be implemented and verified separately.
     */
    fun isRecognizedExchangeExtension(fileName: String): Boolean {
        val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")
        return extension.equals(FILE_EXTENSION, ignoreCase = true) ||
            extension.equals("vzv", ignoreCase = true)
    }

    fun isNpvExtension(fileName: String): Boolean {
        val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")
        return extension.equals("npv", ignoreCase = true) ||
            extension.equals("npvs", ignoreCase = true)
    }
}
