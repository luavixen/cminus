package dev.foxgirl.cminus.util

import java.nio.ByteBuffer
import java.util.*

object UUIDEncoding {
    fun toByteArray(uuid: UUID): ByteArray {
        return ByteBuffer.wrap(ByteArray(16))
            .putLong(uuid.mostSignificantBits)
            .putLong(uuid.leastSignificantBits)
            .array()
    }
    fun fromByteArray(bytes: ByteArray): UUID {
        return ByteBuffer.wrap(bytes).let { UUID(it.getLong(), it.getLong()) }
    }
}
