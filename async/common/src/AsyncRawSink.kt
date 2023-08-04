/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package kotlinx.io.async

import kotlinx.io.Buffer

public interface AsyncRawSink {
    public suspend fun write(buffer: Buffer, bytesCount: Long)
    public suspend fun flush()
    public suspend fun close()
}
