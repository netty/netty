/*
 * Copyright 2017 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty5.microbench.handler.ssl;

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.buffer.api.DefaultBufferAllocators;
import io.netty5.channel.embedded.EmbeddedChannel;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;

import java.util.concurrent.ThreadLocalRandom;

public abstract class AbstractSslHandlerThroughputBenchmark extends AbstractSslHandlerBenchmark {
    @Param({ "64", "128", "512", "1024", "4096" })
    public int messageSize;

    @Param
    public BufferType bufferType;

    public enum BufferType {
        HEAP {
            @Override
            Buffer newBuffer(int size) {
                return DefaultBufferAllocators.onHeapAllocator().allocate(size);
            }
        },
        DIRECT {
            @Override
            Buffer newBuffer(int size) {
                return DefaultBufferAllocators.offHeapAllocator().allocate(size);
            }
        };

        abstract Buffer newBuffer(int size);
    }

    protected Buffer wrapSrcBuffer;
    protected EmbeddedChannel channel;
    private BufferAllocator allocator;

    @Setup(Level.Iteration)
    public final void setup() throws Exception {
        allocator = DefaultBufferAllocators.offHeapAllocator();

        initSslHandlers(allocator);

        wrapSrcBuffer = allocateBuffer(messageSize);

        byte[] bytes = new byte[messageSize];
        ThreadLocalRandom.current().nextBytes(bytes);
        wrapSrcBuffer.writeBytes(bytes).makeReadOnly();

        // Complete the initial TLS handshake.
        doHandshake();
    }

    @TearDown(Level.Iteration)
    public final void tearDown() throws Exception {
        destroySslHandlers();
        wrapSrcBuffer.close();
        clientCtx.releaseCumulation();
        serverCtx.releaseCumulation();
    }

    protected final Buffer allocateBuffer(int size) {
        return bufferType.newBuffer(size);
    }

    protected final Buffer doWrite(int numWrites) throws Exception {
        clientCtx.releaseCumulation();

        for (int i = 0; i < numWrites; ++i) {
            Buffer copy = wrapSrcBuffer.copy(wrapSrcBuffer.readerOffset(), wrapSrcBuffer.readableBytes());
            clientSslHandler.write(clientCtx, copy);
        }
        clientSslHandler.flush(clientCtx);
        return clientCtx.cumulation().split();
    }
}
