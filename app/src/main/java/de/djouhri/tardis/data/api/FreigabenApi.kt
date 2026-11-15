package de.djouhri.tardis.data.api

import de.djouhri.tardis.data.model.tardis.FreigabeErgebnis
import de.djouhri.tardis.data.model.tardis.FreigabenAntwort
import de.djouhri.tardis.data.model.tardis.SchluesselGemeldet
import de.djouhri.tardis.data.model.tardis.SchluesselMelden
import de.djouhri.tardis.data.model.tardis.Unterschrift
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** Pull-Deploy-Freigaben; die Schranke ist der Zielwirt, das Gateway legt nur ab. */
interface FreigabenApi {

    @GET("v1/freigaben")
    suspend fun liste(): FreigabenAntwort

    @POST("v1/freigaben/schluessel")
    suspend fun schluesselMelden(@Body body: SchluesselMelden): SchluesselGemeldet

    @POST("v1/freigaben/{wirt}/{projekt}/{commit}")
    suspend fun freigeben(
        @Path("wirt") wirt: String,
        @Path("projekt") projekt: String,
        @Path("commit") commit: String,
        @Body body: Unterschrift,
    ): FreigabeErgebnis
}
