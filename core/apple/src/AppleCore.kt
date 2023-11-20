/*
 * Copyright 2017-2023 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENCE file.
 */

@file:OptIn(UnsafeNumber::class, ExperimentalForeignApi::class)

package kotlinx.io

import kotlinx.cinterop.*
import kotlinx.io.unsafe.UnsafeBufferAccessors
import platform.Foundation.NSInputStream
import platform.Foundation.NSOutputStream
import platform.Foundation.NSStreamStatusClosed
import platform.Foundation.NSStreamStatusNotOpen
import platform.posix.uint8_tVar

/**
 * Returns [RawSink] that writes to an output stream.
 *
 * Use [RawSink.buffered] to create a buffered sink from it.
 *
 * @sample kotlinx.io.samples.KotlinxIoSamplesApple.outputStreamAsSink
 */
public fun NSOutputStream.asSink(): RawSink = OutputStreamSink(this)

private open class OutputStreamSink(
    private val out: NSOutputStream,
) : RawSink {

    init {
        if (out.streamStatus == NSStreamStatusNotOpen) out.open()
    }

    @OptIn(UnsafeIoApi::class)
    override fun write(source: Buffer, byteCount: Long) {
        if (out.streamStatus == NSStreamStatusClosed) throw IOException("Stream Closed")

        checkOffsetAndCount(source.size, 0, byteCount)
        var remaining = byteCount
        while (remaining > 0) {
            val head = source.head!!
            val toCopy = minOf(remaining, head.size).toInt()
            val bytesWritten = head.withContainedData { data, pos, _ ->
                when (data) {
                    is ByteArray -> {
                        data.usePinned {
                            val bytes = it.addressOf(pos).reinterpret<uint8_tVar>()
                            out.write(bytes, toCopy.convert()).toLong()
                        }
                    }
                    else -> TODO()
                }
            }

            if (bytesWritten < 0L) throw IOException(out.streamError?.localizedDescription ?: "Unknown error")
            if (bytesWritten == 0L) throw IOException("NSOutputStream reached capacity")

            source.skip(bytesWritten)
            remaining -= bytesWritten
        }
    }

    override fun flush() {
        // no-op
    }

    override fun close() = out.close()

    override fun toString() = "RawSink($out)"
}

/**
 * Returns [RawSource] that reads from an input stream.
 *
 * Use [RawSource.buffered] to create a buffered source from it.
 *
 * @sample kotlinx.io.samples.KotlinxIoSamplesApple.inputStreamAsSource
 */
public fun NSInputStream.asSource(): RawSource = NSInputStreamSource(this)

private open class NSInputStreamSource(
    private val input: NSInputStream,
) : RawSource {

    init {
        if (input.streamStatus == NSStreamStatusNotOpen) input.open()
    }

    @OptIn(UnsafeIoApi::class)
    override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
        if (input.streamStatus == NSStreamStatusClosed) throw IOException("Stream Closed")

        if (byteCount == 0L) return 0L
        checkByteCount(byteCount)

        var bytesRead = 0L
        UnsafeBufferAccessors.writeUnbound(sink, 1) {
            val maxToCopy = minOf(byteCount, it.remainingCapacity)
            val read = it.withContainedData { data, _, limit ->
                when (data) {
                    is ByteArray -> {
                        data.usePinned { ba ->
                            val bytes = ba.addressOf(limit).reinterpret<uint8_tVar>()
                            input.read(bytes, maxToCopy.convert()).toLong()
                        }
                    }
                    else -> TODO()
                }
            }
            bytesRead = read
            maxOf(read.toInt(), 0)
        }
        if (bytesRead < 0L) throw IOException(input.streamError?.localizedDescription ?: "Unknown error")
        if (bytesRead == 0L) return -1
        return bytesRead
    }

    override fun close() = input.close()

    override fun toString() = "RawSource($input)"
}
