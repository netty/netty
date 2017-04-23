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
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.PlatformDependent;
import org.openjdk.jmh.annotations.Param;

import java.io.File;
import java.nio.ByteBuffer;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;


public class AbstractSslEngineBenchmark extends AbstractMicrobenchmark {

    private static final String PROTOCOL_TLS_V1_2 = "TLSv1.2";

    public enum SslEngineProvider {
        JDK {
            @Override
            SslProvider sslProvider() {
                return SslProvider.JDK;
            }
        },
        OPENSSL {
            @Override
            SslProvider sslProvider() {
                return SslProvider.OPENSSL;
            }
        },
        OPENSSL_REFCNT {
            @Override
            SslProvider sslProvider() {
                return SslProvider.OPENSSL_REFCNT;
            }
        };
        private final SslContext clientContext = newClientContext();
        private final SslContext serverContext = newServerContext();

        private SslContext newClientContext() {
            try {
                return SslContextBuilder.forClient()
                        .sslProvider(sslProvider())
                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                        .build();
            } catch (SSLException e) {
                throw new IllegalStateException(e);
            }
        }

        private SslContext newServerContext() {
            try {
                File keyFile = new File(getClass().getResource("test_unencrypted.pem").getFile());
                File crtFile = new File(getClass().getResource("test.crt").getFile());

                return SslContextBuilder.forServer(crtFile, keyFile)
                        .sslProvider(sslProvider())
                        .build();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        SSLEngine newClientEngine(ByteBufAllocator allocator, String cipher) {
            return configureEngine(clientContext.newHandler(allocator).engine(), cipher);
        }

        SSLEngine newServerEngine(ByteBufAllocator allocator, String cipher) {
            return configureEngine(serverContext.newHandler(allocator).engine(), cipher);
        }

        abstract SslProvider sslProvider();

        static SSLEngine configureEngine(SSLEngine engine, String cipher) {
            engine.setEnabledProtocols(new String[]{ PROTOCOL_TLS_V1_2 });
            engine.setEnabledCipherSuites(new String[]{ cipher });
            return engine;
        }
    }

    public enum BufferType {
        HEAP {
            @Override
            ByteBuffer newBuffer(int size) {
                return ByteBuffer.allocate(size);
            }
        },
        DIRECT {
            @Override
            ByteBuffer newBuffer(int size) {
                return ByteBuffer.allocateDirect(size);
            }

            @Override
            void freeBuffer(ByteBuffer buffer) {
                PlatformDependent.freeDirectBuffer(buffer);
            }
        };

        abstract ByteBuffer newBuffer(int size);

        void freeBuffer(ByteBuffer buffer) { }
    }

    @Param
    public SslEngineProvider sslProvider;

    @Param
    public BufferType bufferType;

    // Includes cipher required by HTTP/2
    @Param({ "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256", "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256" })
    public String cipher;

    protected SSLEngine clientEngine;
    protected SSLEngine serverEngine;

    private ByteBuffer cTOs;
    private ByteBuffer sTOc;
    private ByteBuffer serverAppReadBuffer;
    private ByteBuffer clientAppReadBuffer;
    private ByteBuffer empty;

    protected final void initEngines(ByteBufAllocator allocator) {
        clientEngine = newClientEngine(allocator);
        serverEngine = newServerEngine(allocator);
    }

    protected final void destroyEngines() {
        ReferenceCountUtil.release(clientEngine);
        ReferenceCountUtil.release(serverEngine);
    }

    protected final void initHandshakeBuffers() {
        cTOs = allocateBuffer(clientEngine.getSession().getPacketBufferSize());
        sTOc = allocateBuffer(serverEngine.getSession().getPacketBufferSize());

        serverAppReadBuffer = allocateBuffer(
                serverEngine.getSession().getApplicationBufferSize());
        clientAppReadBuffer = allocateBuffer(
                clientEngine.getSession().getApplicationBufferSize());
        empty = allocateBuffer(0);
    }

    protected final void destroyHandshakeBuffers() {
        freeBuffer(cTOs);
        freeBuffer(sTOc);
        freeBuffer(serverAppReadBuffer);
        freeBuffer(clientAppReadBuffer);
        freeBuffer(empty);
    }

    protected final boolean doHandshake() throws SSLException {
        clientEngine.beginHandshake();
        serverEngine.beginHandshake();

        SSLEngineResult clientResult = null;
        SSLEngineResult serverResult = null;

        boolean clientHandshakeFinished = false;
        boolean serverHandshakeFinished = false;

        do {
            int cTOsPos = cTOs.position();
            int sTOcPos = sTOc.position();

            if (!clientHandshakeFinished) {
                clientResult = clientEngine.wrap(empty, cTOs);
                runDelegatedTasks(clientResult, clientEngine);
                assert empty.remaining() == clientResult.bytesConsumed();
                assert cTOs.position() - cTOsPos == clientResult.bytesProduced();

                clientHandshakeFinished = isHandshakeFinished(clientResult);
            }

            if (!serverHandshakeFinished) {
                serverResult = serverEngine.wrap(empty, sTOc);
                runDelegatedTasks(serverResult, serverEngine);
                assert empty.remaining() == serverResult.bytesConsumed();
                assert sTOc.position() - sTOcPos == serverResult.bytesProduced();

                serverHandshakeFinished = isHandshakeFinished(serverResult);
            }

            cTOs.flip();
            sTOc.flip();

            cTOsPos = cTOs.position();
            sTOcPos = sTOc.position();

            if (!clientHandshakeFinished) {
                int clientAppReadBufferPos = clientAppReadBuffer.position();
                clientResult = clientEngine.unwrap(sTOc, clientAppReadBuffer);

                runDelegatedTasks(clientResult, clientEngine);
                assert sTOc.position() - sTOcPos == clientResult.bytesConsumed();
                assert clientAppReadBuffer.position() - clientAppReadBufferPos == clientResult.bytesProduced();

                clientHandshakeFinished = isHandshakeFinished(clientResult);
            } else {
                assert !sTOc.hasRemaining();
            }

            if (!serverHandshakeFinished) {
                int serverAppReadBufferPos = serverAppReadBuffer.position();
                serverResult = serverEngine.unwrap(cTOs, serverAppReadBuffer);
                runDelegatedTasks(serverResult, serverEngine);
                assert cTOs.position() - cTOsPos == serverResult.bytesConsumed();
                assert serverAppReadBuffer.position() - serverAppReadBufferPos == serverResult.bytesProduced();

                serverHandshakeFinished = isHandshakeFinished(serverResult);
            } else {
                assert !cTOs.hasRemaining();
            }

            sTOc.compact();
            cTOs.compact();
        } while (!clientHandshakeFinished || !serverHandshakeFinished);
        return clientResult.getStatus() == SSLEngineResult.Status.OK &&
                serverResult.getStatus() == SSLEngineResult.Status.OK;
    }

    protected final SSLEngine newClientEngine(ByteBufAllocator allocator) {
        return sslProvider.newClientEngine(allocator, cipher);
    }

    protected final SSLEngine newServerEngine(ByteBufAllocator allocator) {
        return sslProvider.newServerEngine(allocator, cipher);
    }

    static boolean checkSslEngineResult(SSLEngineResult result, ByteBuffer src, ByteBuffer dst) {
        return result.getStatus() == SSLEngineResult.Status.OK && !src.hasRemaining() && dst.hasRemaining();
    }

    protected final ByteBuffer allocateBuffer(int size) {
        return bufferType.newBuffer(size);
    }

    protected final void freeBuffer(ByteBuffer buffer) {
        bufferType.freeBuffer(buffer);
    }

    private static boolean isHandshakeFinished(SSLEngineResult result) {
        return result.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.FINISHED;
    }

    private static void runDelegatedTasks(SSLEngineResult result, SSLEngine engine) {
        if (result.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_TASK) {
            for (;;) {
                Runnable task = engine.getDelegatedTask();
                if (task == null) {
                    break;
                }
                task.run();
            }
        }
    }
}
