package com.aistudio.neurostats.data

import kotlinx.coroutines.flow.Flow

class EegRepository(private val eegSessionDao: EegSessionDao) {
    val allSessions: Flow<List<EegSession>> = eegSessionDao.getAllSessions()

    fun getSession(id: Int): Flow<EegSession?> {
        return eegSessionDao.getSessionById(id)
    }

    suspend fun insert(session: EegSession) {
        eegSessionDao.insert(session)
    }

    suspend fun delete(session: EegSession) {
        eegSessionDao.delete(session)
    }
}
