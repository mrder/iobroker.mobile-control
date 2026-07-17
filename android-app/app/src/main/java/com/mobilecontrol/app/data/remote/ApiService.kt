package com.mobilecontrol.app.data.remote

import com.mobilecontrol.app.data.remote.dto.CatalogResponseDto
import com.mobilecontrol.app.data.remote.dto.ChallengeRequestDto
import com.mobilecontrol.app.data.remote.dto.ChallengeResponseDto
import com.mobilecontrol.app.data.remote.dto.ClaimRequestDto
import com.mobilecontrol.app.data.remote.dto.ClaimResponseDto
import com.mobilecontrol.app.data.remote.dto.CommandRequestDto
import com.mobilecontrol.app.data.remote.dto.CommandResponseDto
import com.mobilecontrol.app.data.remote.dto.DashboardDto
import com.mobilecontrol.app.data.remote.dto.DashboardListResponseDto
import com.mobilecontrol.app.data.remote.dto.HistoryResponseDto
import com.mobilecontrol.app.data.remote.dto.LoginRequestDto
import com.mobilecontrol.app.data.remote.dto.PairingStatusResponseDto
import com.mobilecontrol.app.data.remote.dto.RefreshRequestDto
import com.mobilecontrol.app.data.remote.dto.StatesResponseDto
import com.mobilecontrol.app.data.remote.dto.TokenResponseDto
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface ApiService {

    @POST("api/v1/pairing/claim")
    suspend fun claimPairing(@Body body: ClaimRequestDto): Response<ClaimResponseDto>

    @GET("api/v1/pairing/status/{claimId}")
    suspend fun pairingStatus(@Path("claimId") claimId: String): Response<PairingStatusResponseDto>

    @POST("api/v1/auth/challenge")
    suspend fun authChallenge(@Body body: ChallengeRequestDto): Response<ChallengeResponseDto>

    @POST("api/v1/auth/login")
    suspend fun authLogin(@Body body: LoginRequestDto): Response<TokenResponseDto>

    @POST("api/v1/auth/refresh")
    suspend fun authRefresh(@Body body: RefreshRequestDto): Response<TokenResponseDto>

    @GET("api/v1/catalog")
    suspend fun getCatalog(): Response<CatalogResponseDto>

    @GET("api/v1/states")
    suspend fun getStates(@Query("ids") ids: String): Response<StatesResponseDto>

    @GET("api/v1/history")
    suspend fun getHistory(
        @Query("id") id: String,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("limit") limit: Int? = null,
    ): Response<HistoryResponseDto>

    @POST("api/v1/commands")
    suspend fun sendCommand(@Body body: CommandRequestDto): Response<CommandResponseDto>

    @Streaming
    @GET("api/v1/objects/{id}/snapshot")
    suspend fun getSnapshot(@Path("id") id: String): Response<ResponseBody>

    @GET("api/v1/dashboards")
    suspend fun getDashboards(): Response<DashboardListResponseDto>

    @POST("api/v1/dashboards")
    suspend fun createDashboard(@Body body: DashboardDto): Response<DashboardDto>

    @PUT("api/v1/dashboards/{id}")
    suspend fun updateDashboard(@Path("id") id: String, @Body body: DashboardDto): Response<DashboardDto>

    @DELETE("api/v1/dashboards/{id}")
    suspend fun deleteDashboard(@Path("id") id: String): Response<Unit>
}
