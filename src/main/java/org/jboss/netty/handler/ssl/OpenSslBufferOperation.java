/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.handler.ssl;

import org.apache.tomcat.jni.Buffer;
import org.jboss.netty.util.internal.EmptyArrays;

import java.nio.ByteBuffer;

abstract class OpenSslBufferOperation {

    private static final RuntimeException ALLOCATION_INTERRUPTED =
            new IllegalStateException("buffer allocation interrupted");

    static {
        ALLOCATION_INTERRUPTED.setStackTrace(EmptyArrays.EMPTY_STACK_TRACE);
    }

    private final OpenSslBufferPool pool;

    OpenSslBufferOperation(OpenSslBufferPool pool) {
        this.pool = pool;

        ByteBuffer buffer = acquireDirectBuffer();
        try {
            run(buffer, Buffer.address(buffer));
        } finally {
            releaseDirectBuffer(buffer);
        }
    }

    private ByteBuffer acquireDirectBuffer() {
        try {
            return pool.acquire();
        } catch (InterruptedException e) {
            throw ALLOCATION_INTERRUPTED;
        }
    }

    private void releaseDirectBuffer(ByteBuffer buffer) {
        buffer.rewind();
        buffer.clear();
        pool.release(buffer);
    }

    abstract void run(ByteBuffer buffer, long address);
}
