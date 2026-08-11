package com.medocr.app.di

import com.medocr.app.data.repository.AuthRepository
import com.medocr.app.data.repository.AuthRepositoryImpl
import com.medocr.app.data.repository.OcrRepository
import com.medocr.app.data.repository.OcrRepositoryImpl
import com.medocr.app.data.repository.SheetsRepository
import com.medocr.app.data.repository.SheetsRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindOcrRepository(impl: OcrRepositoryImpl): OcrRepository

    @Binds
    abstract fun bindSheetsRepository(impl: SheetsRepositoryImpl): SheetsRepository

    @Binds
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository
}
