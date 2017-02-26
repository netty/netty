/*
 * Copyright 2017 The Netty Project
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
package io.netty.microbench.handler.ssl;

import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.util.internal.PlatformDependent;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;

import java.nio.ByteBuffer;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;

public abstract class AbstractSslEngineThroughputBenchmark extends AbstractSslEngineBenchmark {

    @Param({ "64", "128", "512", "1024", "4096" })
    public int messageSize;

    protected ByteBuffer wrapSrcBuffer;
    private ByteBuffer wrapDstBuffer;

    @Setup(Level.Iteration)
    public final void setup() throws Exception {
        ByteBufAllocator allocator = new PooledByteBufAllocator(true);
        initEngines(allocator);
        initHandshakeBuffers();

        wrapDstBuffer = allocateBuffer(clientEngine.getSession().getPacketBufferSize() << 2);
        wrapSrcBuffer = allocateBuffer(messageSize);

        byte[] bytes = new byte[messageSize];
        PlatformDependent.threadLocalRandom().nextBytes(bytes);
        wrapSrcBuffer.put(bytes);
        wrapSrcBuffer.flip();

        // Complete the initial TLS handshake.
        if (!doHandshake()) {
            throw new IllegalStateException();
        }
        doSetup();
    }

    protected void doSetup() throws Exception { }

    @TearDown(Level.Iteration)
    public final void tearDown() throws Exception {
        destroyEngines();
        destroyHandshakeBuffers();
        freeBuffer(wrapSrcBuffer);
        freeBuffer(wrapDstBuffer);
        doTearDown();
    }

    protected void doTearDown() throws Exception { }

    protected final ByteBuffer doWrap(int numWraps) throws SSLException {
        wrapDstBuffer.clear();

        for (int i = 0; i < numWraps; ++i) {
            wrapSrcBuffer.position(0).limit(messageSize);

            SSLEngineResult wrapResult = clientEngine.wrap(wrapSrcBuffer, wrapDstBuffer);

            assert checkSslEngineResult(wrapResult, wrapSrcBuffer, wrapDstBuffer);
        }
        return wrapDstBuffer;
    }
}
