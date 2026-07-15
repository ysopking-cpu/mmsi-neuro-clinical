package com.aistudio.neurostats.data

import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.BufferedReader
import java.io.InputStreamReader

class FilePlaybackDataSource(
    private val context: Context,
    private val fileName: String
) : EegDataSource {

    private var isRunning = false

    override fun getEegDataFlow(): Flow<EegDataFrame> = flow {
        isRunning = true
        try {
            val inputStream = context.assets.open(fileName)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val lines = reader.readLines().filter { it.isNotBlank() && !it.startsWith("#") }
            reader.close()

            var index = 0
            while (isRunning && lines.isNotEmpty()) {
                val line = lines[index % lines.size]
                val parts = line.split(",").mapNotNull { it.trim().toFloatOrNull() }
                
                val channels = if (parts.size >= 4) {
                    floatArrayOf(parts[0], parts[1], parts[2], parts[3])
                } else if (parts.isNotEmpty()) {
                    FloatArray(4) { idx -> if (idx < parts.size) parts[idx] else parts[0] }
                } else {
                    floatArrayOf(0f, 0f, 0f, 0f)
                }

                emit(EegDataFrame(System.currentTimeMillis(), channels))
                index++
                delay(100) // 10Hz playback
            }
        } catch (e: Exception) {
            e.printStackTrace()
            var t = 0f
            while (isRunning) {
                val channels = floatArrayOf(
                    (kotlin.math.sin(t) * 2f + 2f).toFloat(),
                    (kotlin.math.cos(t * 1.5f) * 1.5f + 1.5f).toFloat(),
                    (kotlin.math.sin(t * 0.5f) * 1f + 1f).toFloat(),
                    (Math.random() * 0.5).toFloat()
                )
                emit(EegDataFrame(System.currentTimeMillis(), channels))
                t += 0.1f
                delay(100)
            }
        }
    }

    override fun stopStreaming() {
        isRunning = false
    }
}
