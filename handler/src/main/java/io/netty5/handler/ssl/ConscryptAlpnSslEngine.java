/*
 * Copyright 2017 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty5.handler.ssl;

import io.netty5.buffer.Buffer;
import io.netty5.buffer.DefaultBufferAllocators;
import io.netty5.buffer.StandardAllocationTypes;
import io.netty5.handler.ssl.JdkApplicationProtocolNegotiator.ProtocolSelectionListener;
import io.netty5.handler.ssl.JdkApplicationProtocolNegotiator.ProtocolSelector;
import io.netty5.util.internal.EmptyArrays;
import io.netty5.util.internal.SystemPropertyUtil;
import org.conscrypt.AllocatedBuffer;
import org.conscrypt.BufferAllocator;
import org.conscrypt.Conscrypt;
import org.conscrypt.HandshakeListener;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import static io.netty5.handler.ssl.SslUtils.toSSLHandshakeException;
import static java.lang.Math.min;
import static java.util.Objects.requireNonNull;

/**
 * A {@link JdkSslEngine} that uses the Conscrypt provider or SSL with ALPN.
 */
abstract class ConscryptAlpnSslEngine extends JdkSslEngine implements VectoredUnwrap {
    private static final boolean USE_BUFFER_ALLOCATOR = SystemPropertyUtil.getBoolean(
            "io.netty5.handler.ssl.conscrypt.useBufferAllocator", true);

    static ConscryptAlpnSslEngine newClientEngine(SSLEngine engine, io.netty5.buffer.BufferAllocator alloc,
            JdkApplicationProtocolNegotiator applicationNegotiator) {
        return new ClientEngine(engine, alloc, applicationNegotiator);
    }

    static ConscryptAlpnSslEngine newServerEngine(SSLEngine engine, io.netty5.buffer.BufferAllocator alloc,
            JdkApplicationProtocolNegotiator applicationNegotiator) {
        return new ServerEngine(engine, alloc, applicationNegotiator);
    }

    private ConscryptAlpnSslEngine(SSLEngine engine, io.netty5.buffer.BufferAllocator alloc,
                                   List<String> protocols) {
        super(engine);

        // Configure the Conscrypt engine to use Netty's buffer allocator. This is a trade-off of memory vs
        // performance.
        //
        // If no allocator is provided, the engine will internally allocate a direct buffer of max packet size in
        // order to optimize JNI calls (this happens the first time it is provided a non-direct buffer from the
        // application).
        //
        // Alternatively, if an allocator is provided, no internal buffer will be created and direct buffers will be
        // retrieved from the allocator on-demand.
        if (USE_BUFFER_ALLOCATOR) {
            Conscrypt.setBufferAllocator(engine, new BufferAllocatorAdapter(alloc));
        }

        // Set the list of supported ALPN protocols on the engine.
        Conscrypt.setApplicationProtocols(engine, protocols.toArray(EmptyArrays.EMPTY_STRINGS));
    }

    /**
     * Calculates the maximum size of the encrypted output buffer required to wrap the given plaintext bytes. Assumes
     * as a worst case that there is one TLS record per buffer.
     *
     * @param plaintextBytes the number of plaintext bytes to be wrapped.
     * @param numBuffers the number of buffers that the plaintext bytes are spread across.
     * @return the maximum size of the encrypted output buffer required for the wrap operation.
     */
    final int calculateOutNetBufSize(int plaintextBytes, int numBuffers) {
        // Assuming a max of one frame per component in a composite buffer.
        return calculateSpace(plaintextBytes, numBuffers, Integer.MAX_VALUE);
    }

    /**
     * Calculate the space necessary in an out buffer to hold the max size that the given
     * plaintextBytes and numBuffers can produce when encrypted. Assumes as a worst case
     * that there is one TLS record per buffer.
     * @param plaintextBytes the number of plaintext bytes to be wrapped.
     * @param numBuffers the number of buffers that the plaintext bytes are spread across.
     * @return the maximum size of the encrypted output buffer required for the wrap operation.
     */
    final int calculateRequiredOutBufSpace(int plaintextBytes, int numBuffers) {
        return calculateSpace(plaintextBytes, numBuffers, Conscrypt.maxEncryptedPacketLength());
    }

    private int calculateSpace(int plaintextBytes, int numBuffers, long maxPacketLength) {
         long maxOverhead = (long) Conscrypt.maxSealOverhead(getWrappedEngine()) * numBuffers;
         return (int) min(maxPacketLength, plaintextBytes + maxOverhead);
    }

    @Override
    public final SSLEngineResult unwrap(ByteBuffer[] srcs, ByteBuffer[] dests) throws SSLException {
        return Conscrypt.unwrap(getWrappedEngine(), srcs, dests);
    }

    private static final class ClientEngine extends ConscryptAlpnSslEngine {
        private final ProtocolSelectionListener protocolListener;

        ClientEngine(SSLEngine engine, io.netty5.buffer.BufferAllocator alloc,
                     JdkApplicationProtocolNegotiator applicationNegotiator) {
            super(engine, alloc, applicationNegotiator.protocols());
            // Register for completion of the handshake.
            Conscrypt.setHandshakeListener(engine, new HandshakeListener() {
                @Override
                public void onHandshakeFinished() throws SSLException {
                    selectProtocol();
                }
            });

            protocolListener = requireNonNull(applicationNegotiator
                            .protocolListenerFactory().newListener(this, applicationNegotiator.protocols()),
                    "protocolListener");
        }

        private void selectProtocol() throws SSLException {
            String protocol = Conscrypt.getApplicationProtocol(getWrappedEngine());
            try {
                protocolListener.selected(protocol);
            } catch (Throwable e) {
                throw toSSLHandshakeException(e);
            }
        }
    }

    private static final class ServerEngine extends ConscryptAlpnSslEngine {
        private final ProtocolSelector protocolSelector;

        ServerEngine(SSLEngine engine, io.netty5.buffer.BufferAllocator alloc,
                     JdkApplicationProtocolNegotiator applicationNegotiator) {
            super(engine, alloc, applicationNegotiator.protocols());

            // Register for completion of the handshake.
            Conscrypt.setHandshakeListener(engine, new HandshakeListener() {
                @Override
                public void onHandshakeFinished() throws SSLException {
                    selectProtocol();
                }
            });

            protocolSelector = requireNonNull(applicationNegotiator.protocolSelectorFactory()
                            .newSelector(this,
                                    new LinkedHashSet<>(applicationNegotiator.protocols())),
                    "protocolSelector");
        }

        private void selectProtocol() throws SSLException {
            try {
                String protocol = Conscrypt.getApplicationProtocol(getWrappedEngine());
                protocolSelector.select(protocol != null ? Collections.singletonList(protocol)
                        : Collections.emptyList());
            } catch (Throwable e) {
                throw toSSLHandshakeException(e);
            }
        }
    }

    private static final class BufferAllocatorAdapter extends BufferAllocator {
        private final io.netty5.buffer.BufferAllocator alloc;

        BufferAllocatorAdapter(io.netty5.buffer.BufferAllocator alloc) {
            if (alloc.getAllocationType() != StandardAllocationTypes.OFF_HEAP) {
                alloc = DefaultBufferAllocators.offHeapAllocator();
            }
            this.alloc = alloc;
        }

        @Override
        public AllocatedBuffer allocateDirectBuffer(int capacity) {
            return new BufferAdapter(alloc, capacity);
        }
    }

    private static final class BufferAdapter extends AllocatedBuffer {
        private final Buffer nettyBuffer;
        private final ByteBuffer buffer;

        BufferAdapter(io.netty5.buffer.BufferAllocator allocator, int capacity) {
            nettyBuffer = allocator.allocate(capacity);

            // Capture internal writable ByteBuffer.
            // It is fine for the ByteBuffer to outlive the iteration here, because we own the Netty buffer,
            // and control its lifetime.
            try (var iteration = nettyBuffer.forEachComponent()) {
                buffer = iteration.firstWritable().writableBuffer();
            }
        }

        @Override
        public ByteBuffer nioBuffer() {
            return buffer;
        }

        @Override
        public AllocatedBuffer retain() {
            throw new UnsupportedOperationException("This method is not supposed to be used.");
        }

        @Override
        public AllocatedBuffer release() {
            nettyBuffer.close();
            return this;
        }
    }
}
