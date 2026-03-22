package com.sunfeld.smsgateway

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Retrofit service interface for the Mission Control Gateway API.
 * Defines the HTTP endpoints used to manage SMS Gateway installation.
 */
interface GatewayApiService {

    /**
     * Triggers gateway installation for a given project.
     *
     * @param id The project identifier (e.g. "sms-gateway-app")
     * @return Call wrapping the install response payload
     */
    @POST("/api/projects/{id}/install-gateway")
    fun installGateway(
        @Path("id") id: String,
        @Body body: Map<String, String> = emptyMap()
    ): Call<InstallGatewayResponse>
}

/**
 * Response payload from the install-gateway endpoint.
 */
data class InstallGatewayResponse(
    val ok: Boolean,
    val jobId: String,
    val status: String,
    val message: String,
    val statusUrl: String
)
