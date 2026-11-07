package de.djouhri.tardis.data.api

import de.djouhri.tardis.data.model.tardis.InboxActionRequest
import de.djouhri.tardis.data.model.tardis.InboxList
import de.djouhri.tardis.data.model.tardis.InboxNoteRequest
import de.djouhri.tardis.data.model.tardis.InboxNoteResponse
import de.djouhri.tardis.data.model.tardis.StreamItem
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface InboxApi {

    @GET("v1/inbox")
    suspend fun list(
        @Query("limit") limit: Int = 30,
        @Query("include_done") includeDone: Boolean = false,
    ): InboxList

    @POST("v1/inbox/note")
    suspend fun createNote(@Body request: InboxNoteRequest): InboxNoteResponse

    @POST("v1/inbox/{external_id}/action")
    suspend fun action(
        @Path("external_id") externalId: String,
        @Body request: InboxActionRequest,
    ): ResponseBody

    /** Zeitleiste der Ereignisse, juengste zuerst (`mobile-api` `routers/stream.py`). */
    @GET("v1/stream")
    suspend fun stream(
        @Query("limit") limit: Int = 120,
        /** CSV; null = alle. Gefiltert wird am Dienst, damit das Fenster 120 Treffer dieses Typs zeigt. */
        @Query("kinds") kinds: String? = null,
        @Query("sources") sources: String? = null,
    ): List<StreamItem>
}
