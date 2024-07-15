/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package kotlinx.io

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PoolingTest {
    @Test
    fun segmentSharing() {
        val buffer = Buffer()

        buffer.writeByte(1)
        var poolSize = SegmentPool.byteCount
        buffer.clear()
        // clear should return a segment to a pool, so the pool size should grow
        assertTrue(poolSize < SegmentPool.byteCount)

        buffer.writeByte(1)
        poolSize = SegmentPool.byteCount
        val copy = buffer.copy()
        buffer.clear()
        copy.clear()
        assertTrue(poolSize < SegmentPool.byteCount)

        buffer.writeByte(1)
        poolSize = SegmentPool.byteCount
        val peek = buffer.peek().buffered()
        peek.readByte()
        buffer.clear()
        assertTrue(poolSize < SegmentPool.byteCount)

        buffer.writeByte(1)
        poolSize = SegmentPool.byteCount
        val otherBuffer = Buffer()
        otherBuffer.write(buffer, buffer.size)
        otherBuffer.clear()
        assertTrue(poolSize < SegmentPool.byteCount)
    }

    @Test
    fun secondLevelPoolCapacityTest() {
        val segments = mutableSetOf<Segment>()

        val maxPoolSize = (SegmentPool.MAX_SIZE + SegmentPool.SECOND_LEVEL_POOL_TOTAL_SIZE) / Segment.SIZE
        repeat(maxPoolSize) {
            assertTrue(segments.add(SegmentPool.take()))
        }

        segments.forEach { SegmentPool.recycle(it) }
        repeat(maxPoolSize) {
            assertFalse(segments.add(SegmentPool.take()))
        }
        assertTrue(segments.add(SegmentPool.take()))

        segments.forEach { SegmentPool.recycle(it) }
        segments.clear()
    }
}
