package com.mikeliang.questtracker.health

import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/**
 * Where the app stands with Health Connect. The UI decides what each state means
 * (ask, explain, or quietly stay in manual mode) — this module only reports.
 */
enum class HealthPermissionStatus {
    /** Every read permission we need is granted. */
    Granted,

    /** Health Connect exists but at least one read permission is missing. */
    Denied,

    /** No usable Health Connect on this device (not installed, or provider too old). */
    Unavailable,
}

/** Permission state reporting for the UI. No UI, no permission *requests*, live here. */
class HealthConnectPermissions @Inject constructor(
    private val api: HealthConnectApi,
) {

    /** The permission strings the UI's request contract should ask for. */
    val requiredPermissions: Set<String> get() = api.readPermissions

    /**
     * Current status. Never throws: a failing status check means we cannot read
     * health data right now, which is [HealthPermissionStatus.Unavailable] — the
     * same "offer manual completion" answer as a missing provider.
     */
    suspend fun status(): HealthPermissionStatus {
        if (api.availability() != HealthConnectAvailability.Installed) {
            return HealthPermissionStatus.Unavailable
        }
        return try {
            if (api.grantedPermissions().containsAll(api.readPermissions)) {
                HealthPermissionStatus.Granted
            } else {
                HealthPermissionStatus.Denied
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            HealthPermissionStatus.Unavailable
        }
    }
}
