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

import io.netty5.buffer.ByteBuf;
import io.netty5.buffer.ByteBufAllocator;
import io.netty5.buffer.PooledByteBufAllocator;
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
            ByteBuf newBuffer(ByteBufAllocator allocator, int size) {
                return allocator.heapBuffer(size);
            }
        },
        DIRECT {
            @Override
            ByteBuf newBuffer(ByteBufAllocator allocator, int size) {
                return allocator.directBuffer(size);
            }
        };

        abstract ByteBuf newBuffer(ByteBufAllocator allocator, int size);
    }

    protected ByteBuf wrapSrcBuffer;
    protected EmbeddedChannel channel;
    private ByteBufAllocator allocator;

    @Setup(Level.Iteration)
    public final void setup() throws Exception {
        allocator = new PooledByteBufAllocator(true);

        initSslHandlers(allocator);

        wrapSrcBuffer = allocateBuffer(messageSize);

        byte[] bytes = new byte[messageSize];
        ThreadLocalRandom.current().nextBytes(bytes);
        wrapSrcBuffer.writeBytes(bytes);

        // Complete the initial TLS handshake.
        doHandshake();
    }

    @TearDown(Level.Iteration)
    public final void tearDown() throws Exception {
        destroySslHandlers();
        wrapSrcBuffer.release();
        clientCtx.releaseCumulation();
        serverCtx.releaseCumulation();
    }

    protected final ByteBuf allocateBuffer(int size) {
        return bufferType.newBuffer(allocator, size);
    }

    protected final ByteBuf doWrite(int numWrites) throws Exception {
        clientCtx.releaseCumulation();

        for (int i = 0; i < numWrites; ++i) {
            ByteBuf wrapSrcBuffer = this.wrapSrcBuffer.retainedSlice();

            clientSslHandler.write(clientCtx, wrapSrcBuffer);
        }
        clientSslHandler.flush(clientCtx);
        return clientCtx.cumulation().retainedSlice();
    }
}
