package com.mikeliang.questtracker.health

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HealthConnectPermissionsTest {

    private val api = FakeHealthConnectApi()
    private val permissions = HealthConnectPermissions(api)

    @Test
    fun `all read permissions granted reports Granted`() = runTest {
        api.granted = api.readPermissions

        assertEquals(HealthPermissionStatus.Granted, permissions.status())
    }

    @Test
    fun `a missing permission reports Denied`() = runTest {
        api.granted = api.readPermissions - "android.permission.health.READ_SLEEP"

        assertEquals(HealthPermissionStatus.Denied, permissions.status())
    }

    @Test
    fun `nothing granted reports Denied`() = runTest {
        api.granted = emptySet()

        assertEquals(HealthPermissionStatus.Denied, permissions.status())
    }

    @Test
    fun `no Health Connect on the device reports Unavailable`() = runTest {
        api.availability = HealthConnectAvailability.NotInstalled

        assertEquals(HealthPermissionStatus.Unavailable, permissions.status())
    }

    @Test
    fun `a provider needing an update reports Unavailable`() = runTest {
        api.availability = HealthConnectAvailability.UpdateRequired

        assertEquals(HealthPermissionStatus.Unavailable, permissions.status())
    }

    @Test
    fun `a failing permission check reports Unavailable, not an exception`() = runTest {
        api.grantedPermissionsError = IllegalStateException("binder died")

        assertEquals(HealthPermissionStatus.Unavailable, permissions.status())
    }

    @Test
    fun `exposes the permission strings the UI must request`() {
        assertEquals(api.readPermissions, permissions.requiredPermissions)
    }
}
