package de.djouhri.tardis.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.djouhri.tardis.BuildConfig
import de.djouhri.tardis.security.MtlsProvider
import de.djouhri.tardis.security.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Interne Kopfzeile: verlaengert fuer EINEN Aufruf die Lesefrist (Sekunden).
 *
 * Sie verlaesst das Telefon nie, [fristInterceptor] nimmt sie vor dem Senden
 * wieder heraus. Der Weg ueber eine Annotation statt eines zweiten
 * OkHttpClient haelt Token, Basis-URL und mTLS an genau einer Stelle.
 */
const val LANGE_FRIST_HEADER = "X-Tardis-Frist"

internal val fristInterceptor = Interceptor { chain ->
    val request = chain.request()
    val sekunden = request.header(LANGE_FRIST_HEADER)?.toIntOrNull()
    if (sekunden == null) {
        chain.proceed(request)
    } else {
        chain.withReadTimeout(sekunden, TimeUnit.SECONDS)
            .proceed(request.newBuilder().removeHeader(LANGE_FRIST_HEADER).build())
    }
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideOkHttpClient(
        sessionState: SessionState,
        mtlsProvider: MtlsProvider,
    ): OkHttpClient {
        // Setzt Bearer-Token + aktuelle Gateway-Basis-URL. Liest den Zustand
        // NON-BLOCKING aus dem In-Memory-SessionState (kein runBlocking im Hot-Path).
        val authInterceptor = Interceptor { chain ->
            val token = sessionState.token
            val target = sessionState.gatewayBaseUrl().toHttpUrlOrNull()

            var request = chain.request()
            if (target != null) {
                val rewritten = request.url.newBuilder()
                    .scheme(target.scheme)
                    .host(target.host)
                    .port(target.port)
                    .build()
                request = request.newBuilder().url(rewritten).build()
            }
            if (!token.isNullOrBlank()) {
                request = request.newBuilder().addHeader("Authorization", "Bearer $token").build()
            }

            val response = chain.proceed(request)
            when {
                // Abgelaufenes/ungueltiges Token: NUR signalisieren, NICHT den
                // kompletten Pairing-Zustand (Keystore-Key/Cert) zerstoeren. Ein
                // transienter 401 soll kein Zwangs-Re-Pairing ausloesen.
                response.code == 401 && !token.isNullOrBlank() -> sessionState.reportUnauthorized()
                response.isSuccessful -> sessionState.clearUnauthorized()
            }
            response
        }

        // Kein BODY-Logging (leakte sonst Token/Payloads im Log).
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.NONE
        }

        val builder = OkHttpClient.Builder()
            .addInterceptor(fristInterceptor)
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        // mTLS: praesentiert das geraetegebundene Client-Cert und pinnt die
        // Homelab-CA, sobald das Gateway per TLS antwortet. Ueber Klartext-HTTP
        // (Phase 1) bleibt es inert. Schlaegt der Aufbau fehl, laeuft die App
        // ohne Client-Cert weiter (Server-Auth bleibt zustaendig).
        runCatching {
            val trustManager = mtlsProvider.trustManager()
            builder.sslSocketFactory(mtlsProvider.socketFactory(trustManager), trustManager)
        }

        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            // Platzhalter-Basis; die tatsaechliche Gateway-Adresse setzt der
            // Auth-Interceptor pro Request aus dem SessionState (QR-gateway_url).
            .baseUrl(normalizeBaseUrl(BuildConfig.GATEWAY_URL))
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    private fun normalizeBaseUrl(url: String): String =
        if (url.endsWith("/")) url else "$url/"
}
