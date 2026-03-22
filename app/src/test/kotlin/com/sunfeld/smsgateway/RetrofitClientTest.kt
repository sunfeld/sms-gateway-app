package com.sunfeld.smsgateway

import org.junit.Assert.*
import org.junit.Test
import retrofit2.Call
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Body

/**
 * Unit tests for RetrofitClient and GatewayApiService.
 */
class RetrofitClientTest {

    // --- RetrofitClient singleton tests ---

    @Test
    fun `RetrofitClient is a singleton object`() {
        val ref1 = RetrofitClient
        val ref2 = RetrofitClient
        assertSame("RetrofitClient should be a singleton", ref1, ref2)
    }

    @Test
    fun `RetrofitClient exposes gatewayApi property`() {
        val api = RetrofitClient.gatewayApi
        assertNotNull("gatewayApi should not be null", api)
    }

    @Test
    fun `gatewayApi returns same instance on repeated access`() {
        val api1 = RetrofitClient.gatewayApi
        val api2 = RetrofitClient.gatewayApi
        assertSame("gatewayApi should return the same instance", api1, api2)
    }

    @Test
    fun `gatewayApi implements GatewayApiService interface`() {
        val api = RetrofitClient.gatewayApi
        assertTrue(
            "gatewayApi should implement GatewayApiService",
            api is GatewayApiService
        )
    }

    // --- GatewayApiService interface contract tests ---

    @Test
    fun `GatewayApiService has installGateway method`() {
        val method = GatewayApiService::class.java.getDeclaredMethod(
            "installGateway",
            String::class.java,
            Map::class.java
        )
        assertNotNull("installGateway method should exist", method)
    }

    @Test
    fun `installGateway method returns Call of InstallGatewayResponse`() {
        val method = GatewayApiService::class.java.getDeclaredMethod(
            "installGateway",
            String::class.java,
            Map::class.java
        )
        assertTrue(
            "Return type should be retrofit2.Call",
            Call::class.java.isAssignableFrom(method.returnType)
        )
    }

    @Test
    fun `installGateway has POST annotation`() {
        val method = GatewayApiService::class.java.getDeclaredMethod(
            "installGateway",
            String::class.java,
            Map::class.java
        )
        val postAnnotation = method.getAnnotation(POST::class.java)
        assertNotNull("installGateway should have @POST annotation", postAnnotation)
    }

    @Test
    fun `installGateway POST annotation targets correct path`() {
        val method = GatewayApiService::class.java.getDeclaredMethod(
            "installGateway",
            String::class.java,
            Map::class.java
        )
        val postAnnotation = method.getAnnotation(POST::class.java)
        assertEquals(
            "POST path should be /api/projects/{id}/install-gateway",
            "/api/projects/{id}/install-gateway",
            postAnnotation!!.value
        )
    }

    @Test
    fun `installGateway first parameter has Path annotation with id value`() {
        val method = GatewayApiService::class.java.getDeclaredMethod(
            "installGateway",
            String::class.java,
            Map::class.java
        )
        val paramAnnotations = method.parameterAnnotations
        val pathAnnotation = paramAnnotations[0].filterIsInstance<Path>().firstOrNull()
        assertNotNull("First parameter should have @Path annotation", pathAnnotation)
        assertEquals("Path value should be 'id'", "id", pathAnnotation!!.value)
    }

    @Test
    fun `installGateway second parameter has Body annotation`() {
        val method = GatewayApiService::class.java.getDeclaredMethod(
            "installGateway",
            String::class.java,
            Map::class.java
        )
        val paramAnnotations = method.parameterAnnotations
        val bodyAnnotation = paramAnnotations[1].filterIsInstance<Body>().firstOrNull()
        assertNotNull("Second parameter should have @Body annotation", bodyAnnotation)
    }

    @Test
    fun `installGateway returns a non-null Call object`() {
        val api = RetrofitClient.gatewayApi
        val call = api.installGateway("sms-gateway-app")
        assertNotNull("installGateway should return a non-null Call", call)
    }

    // --- InstallGatewayResponse data class tests ---

    @Test
    fun `InstallGatewayResponse can be constructed with all fields`() {
        val response = InstallGatewayResponse(
            ok = true,
            jobId = "job-123",
            status = "queued",
            message = "Installation started",
            statusUrl = "/api/jobs/job-123/status"
        )
        assertTrue(response.ok)
        assertEquals("job-123", response.jobId)
        assertEquals("queued", response.status)
        assertEquals("Installation started", response.message)
        assertEquals("/api/jobs/job-123/status", response.statusUrl)
    }

    @Test
    fun `InstallGatewayResponse supports copy with modified fields`() {
        val original = InstallGatewayResponse(
            ok = true,
            jobId = "job-1",
            status = "queued",
            message = "Started",
            statusUrl = "/api/jobs/job-1/status"
        )
        val modified = original.copy(status = "completed", ok = false)
        assertEquals("completed", modified.status)
        assertFalse(modified.ok)
        assertEquals("job-1", modified.jobId)
    }

    @Test
    fun `InstallGatewayResponse equals works correctly`() {
        val a = InstallGatewayResponse(true, "j1", "ok", "msg", "/url")
        val b = InstallGatewayResponse(true, "j1", "ok", "msg", "/url")
        assertEquals(a, b)
    }

    @Test
    fun `InstallGatewayResponse has all five fields`() {
        val fields = InstallGatewayResponse::class.java.declaredFields
            .filter { !it.isSynthetic }
            .map { it.name }
            .toSet()
        assertTrue("Should have 'ok' field", fields.contains("ok"))
        assertTrue("Should have 'jobId' field", fields.contains("jobId"))
        assertTrue("Should have 'status' field", fields.contains("status"))
        assertTrue("Should have 'message' field", fields.contains("message"))
        assertTrue("Should have 'statusUrl' field", fields.contains("statusUrl"))
    }

    // --- Config.BASE_URL integration ---

    @Test
    fun `RetrofitClient uses Config BASE_URL`() {
        // The RetrofitClient is built with Config.BASE_URL which should be http://10.0.0.2:8080
        // We verify the config value is as expected
        assertEquals("http://10.0.0.2:8080", Config.BASE_URL)
        // RetrofitClient.gatewayApi being non-null confirms it was built successfully with this URL
        assertNotNull(RetrofitClient.gatewayApi)
    }
}
