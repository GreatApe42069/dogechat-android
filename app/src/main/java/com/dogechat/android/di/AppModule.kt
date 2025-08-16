package com.dogechat.android.di

import android.content.Context
import com.dogechat.android.WalletManager
import com.dogechat.android.mesh.BluetoothMeshService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideWalletManager(
        @ApplicationContext appContext: Context
    ): WalletManager = WalletManager(appContext)

    @Provides
    @Singleton
    fun provideBluetoothMeshService(@ApplicationContext context: Context): BluetoothMeshService {
        return BluetoothMeshService(context).apply { startServices() }
    }
}
