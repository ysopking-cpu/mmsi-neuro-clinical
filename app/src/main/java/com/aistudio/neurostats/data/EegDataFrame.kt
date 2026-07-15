package com.aistudio.neurostats.data

data class EegDataFrame(
    val timestamp: Long,
    val channels: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EegDataFrame) return false
        if (timestamp != other.timestamp) return false
        if (!channels.contentEquals(other.channels)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = timestamp.hashCode()
        result = 31 * result + channels.contentHashCode()
        return result
    }
}
