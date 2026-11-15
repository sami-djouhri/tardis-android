package de.djouhri.tardis.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.djouhri.tardis.data.api.DashboardApi
import de.djouhri.tardis.data.api.FreigabenApi
import de.djouhri.tardis.data.api.InboxApi
import de.djouhri.tardis.data.api.OpsApi
import de.djouhri.tardis.data.api.PairingApi
import de.djouhri.tardis.data.api.VersionApi
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ApiModule {

    @Provides
    @Singleton
    fun providePairingApi(retrofit: Retrofit): PairingApi = retrofit.create(PairingApi::class.java)

    @Provides
    @Singleton
    fun provideOpsApi(retrofit: Retrofit): OpsApi = retrofit.create(OpsApi::class.java)

    @Provides
    @Singleton
    fun provideInboxApi(retrofit: Retrofit): InboxApi = retrofit.create(InboxApi::class.java)

    @Provides
    @Singleton
    fun provideDashboardApi(retrofit: Retrofit): DashboardApi = retrofit.create(DashboardApi::class.java)

    @Provides
    @Singleton
    fun provideVersionApi(retrofit: Retrofit): VersionApi = retrofit.create(VersionApi::class.java)

    @Provides
    @Singleton
    fun provideFreigabenApi(retrofit: Retrofit): FreigabenApi = retrofit.create(FreigabenApi::class.java)
}
