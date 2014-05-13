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

import org.jboss.netty.util.internal.EmptyArrays;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Manages a pool of directly-allocated ByteBuffers.
 *
 * This is necessary as the reclamation of these buffers does not work appropriately
 * on some platforms.
 *
 * TODO: Attempt to replace the directly-allocated ByteBuffers this with one APR pool.
 */
public final class OpenSslBufferPool {

    private static final RuntimeException ALLOCATION_INTERRUPTED =
            new IllegalStateException("buffer allocation interrupted");

    static {
        ALLOCATION_INTERRUPTED.setStackTrace(EmptyArrays.EMPTY_STACK_TRACE);
    }

    private final BlockingQueue<ByteBuffer> buffers;

    /**
     * Construct a new pool with the specified capacity.
     *
     * @param capacity The number of buffers to instantiate.
     */
    public OpenSslBufferPool(int capacity) {
        OpenSsl.ensureAvailability();
        buffers = new LinkedBlockingQueue<ByteBuffer>(capacity);
        while (buffers.remainingCapacity() > 0) {
            ByteBuffer buf = ByteBuffer.allocateDirect(OpenSslEngine.MAX_ENCRYPTED_PACKET_LENGTH);
            buf.order(ByteOrder.nativeOrder());
            buffers.offer(buf);
        }
    }

    /**
     * Take a buffer from the pool.
     *
     * @return a ByteBuffer.
     */
    public ByteBuffer acquire() {
        try {
            return buffers.take();
        } catch (InterruptedException ignore) {
            throw ALLOCATION_INTERRUPTED;
        }
    }

    /**
     * Release a buffer back into the stream
     *
     * @param buffer the ByteBuffer to release
     */
    public void release(ByteBuffer buffer) {
        buffer.clear();
        buffers.offer(buffer);
    }

    @Override
    public String toString() {
        return "[DirectBufferPool " +
                buffers.size() + " buffers * " +
                OpenSslEngine.MAX_ENCRYPTED_PACKET_LENGTH + " bytes = " +
                buffers.size() * OpenSslEngine.MAX_ENCRYPTED_PACKET_LENGTH + " total bytes; " +
                "size: " + buffers.size() +
                " remainingCapacity: " + buffers.remainingCapacity() +
                ']';
    }
}
