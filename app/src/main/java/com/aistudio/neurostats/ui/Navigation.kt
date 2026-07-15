package com.aistudio.neurostats.ui

import kotlinx.serialization.Serializable

@Serializable
object Dashboard

@Serializable
data class SessionDetail(val sessionId: Int)
