package com.aistudio.neurostats.data

import kotlinx.coroutines.flow.Flow

interface EegDataSource {
    fun getEegDataFlow(): Flow<EegDataFrame>
    fun stopStreaming()
}
