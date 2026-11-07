package de.djouhri.tardis.data.api

import de.djouhri.tardis.data.model.tardis.AbrufResponse
import de.djouhri.tardis.data.model.tardis.AlarmeResponse
import de.djouhri.tardis.data.model.tardis.DienstDetail
import de.djouhri.tardis.data.model.tardis.HostsResponse
import de.djouhri.tardis.data.model.tardis.LabErgebnis
import de.djouhri.tardis.data.model.tardis.LogsResponse
import de.djouhri.tardis.data.model.tardis.ReliabilityResponse
import de.djouhri.tardis.data.model.tardis.TopologieResponse
import de.djouhri.tardis.data.model.tardis.ServiceActionResult
import de.djouhri.tardis.data.model.tardis.ServiceSummary
import de.djouhri.tardis.data.model.tardis.WartungErgebnis
import de.djouhri.tardis.data.model.tardis.WeckErgebnis
import de.djouhri.tardis.di.LANGE_FRIST_HEADER
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface OpsApi {

    /** Steuerbare Hosts samt Zaehlstand, ohne alle Container zu uebertragen. */
    @GET("v1/ops/hosts")
    suspend fun hosts(): HostsResponse

    @GET("v1/ops/services")
    suspend fun services(@Query("host") host: String? = null): List<ServiceSummary>

    /** action ist ein Query-Param (kein Body): ?action=start|stop|restart */
    @POST("v1/ops/services/{host}/{name}/action")
    suspend fun action(
        @Path("host") host: String,
        @Path("name") name: String,
        @Query("action") action: String,
    ): ServiceActionResult

    /** Laufzeit, Verbrauch, Wirt, Verlauf und die Alarme genau dieses Containers. */
    @GET("v1/ops/services/{host}/{name}/detail")
    suspend fun detail(
        @Path("host") host: String,
        @Path("name") name: String,
    ): DienstDetail

    @GET("v1/ops/logs/{host}/{name}")
    suspend fun logs(
        @Path("host") host: String,
        @Path("name") name: String,
        @Query("lines") lines: Int = 200,
    ): LogsResponse

    /** Aktive Prometheus-Alarme, nach Schwere und Alter sortiert. */
    @GET("v1/ops/alarme")
    suspend fun alarme(): AlarmeResponse

    /** Verfuegbarkeit wie die Web-Flaeche Reliability; `fenster` 60, 360 oder 1440 Minuten. */
    @GET("v1/ops/reliability")
    suspend fun reliability(@Query("fenster") fenster: Int = 60): ReliabilityResponse

    /** Wirte, Dienste und Verbindungen fuer die Karte (verschlankt aus Tardis /api/topology/graph). */
    @GET("v1/ops/topologie")
    suspend fun topologie(): TopologieResponse

    /** Dienste des Weckers und das AD-Lab. */
    @GET("v1/ops/abruf")
    suspend fun abruf(): AbrufResponse

    @POST("v1/ops/abruf/{name}/wecken")
    suspend fun wecken(@Path("name") name: String): WeckErgebnis

    @POST("v1/ops/abruf/{name}/wartung")
    suspend fun wartung(@Path("name") name: String, @Query("an") an: Boolean): WartungErgebnis

    /**
     * start | stop | start-wartung | wartung-an | wartung-aus.
     *
     * Die wake-bridge haelt einen VM-Start bis zu 200 s. Mit den 30 s des
     * Clients brach die App ab, waehrend der Start auf dem Node weiterlief,
     * und meldete einen Fehler fuer etwas, das gelang.
     */
    @Headers("$LANGE_FRIST_HEADER: 210")
    @POST("v1/ops/lab/{aktion}")
    suspend fun lab(@Path("aktion") aktion: String): LabErgebnis
}
