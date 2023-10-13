/*
 * Copyright 2017-2023 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENCE file.
 */

/*
 * Copyright (C) 2014 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kotlinx.io

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Returns [RawSink] that writes to an output stream.
 *
 * Use [RawSink.buffered] to create a buffered sink from it.
 *
 * @sample kotlinx.io.samples.KotlinxIoSamplesJvm.outputStreamAsSink
 */
public fun OutputStream.asSink(): RawSink = OutputStreamSink(this)

private open class OutputStreamSink(
    private val out: OutputStream,
) : RawSink {

    override fun write(source: Buffer, byteCount: Long) {
        checkOffsetAndCount(source.size, 0, byteCount)
        var remaining = byteCount
        while (remaining > 0) {
            // kotlinx.io TODO: detect Interruption.
            val head = source.head!!
            val toCopy = minOf(remaining, head.size).toInt()

            head.withContainedData { data, pos, _ ->
                when (data) {
                    is ByteArray -> {
                        out.write(data, pos, toCopy)
                    }
                    else -> {
                        TODO()
                        /*
                        for (idx in 0 until toCopy) {
                            out.write(head[idx].toInt())
                        }
                         */
                    }
                }
            }

            remaining -= toCopy
            source.skip(toCopy.toLong())
        }
    }

    override fun flush() = out.flush()

    override fun close() = out.close()

    override fun toString() = "RawSink($out)"
}

/**
 * Returns [RawSource] that reads from an input stream.
 *
 * Use [RawSource.buffered] to create a buffered source from it.
 *
 * @sample kotlinx.io.samples.KotlinxIoSamplesJvm.inputStreamAsSource
 */
public fun InputStream.asSource(): RawSource = InputStreamSource(this)

private open class InputStreamSource(
    private val input: InputStream,
) : RawSource {

    override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
        if (byteCount == 0L) return 0L
        checkByteCount(byteCount)
        try {
            var readTotal = 0L
            sink.writeUnbound(1) {
                val maxToCopy = minOf(byteCount, it.capacity).toInt()
                it.withContainedData { data, _, limit ->
                    when (data) {
                        is ByteArray -> {
                            readTotal = input.read(data, limit, maxToCopy).toLong()
                            if (readTotal == -1L) {
                                0
                            } else {
                                readTotal.toInt()
                            }
                        }
                        else -> {
                            TODO()
                            /*
                            while (readTotal < maxToCopy) {
                                val b = input.read()
                                if (b == -1) break
                                it[readTotal.toInt()] = b.toByte()
                                readTotal++
                            }
                            readTotal.toInt()
                             */
                        }
                    }
                }
            }
            return readTotal
        } catch (e: AssertionError) {
            if (e.isAndroidGetsocknameError) throw IOException(e)
            throw e
        }
    }

    override fun close() = input.close()

    override fun toString() = "RawSource($input)"
}

/**
 * Returns true if this error is due to a firmware bug fixed after Android 4.2.2.
 * https://code.google.com/p/android/issues/detail?id=54072
 */
internal val AssertionError.isAndroidGetsocknameError: Boolean
    get() {
        return cause != null && message?.contains("getsockname failed") ?: false
    }
