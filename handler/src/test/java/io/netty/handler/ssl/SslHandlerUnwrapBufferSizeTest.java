/*
 * Copyright 2026 The Netty Project
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
package io.netty.handler.ssl;

import io.netty.buffer.AbstractByteBufAllocator;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.ssl.util.CachedSelfSignedCertificate;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.security.Provider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Verifies that the buffer {@link SslHandler} unwraps a full TLS record into is sized for the plaintext and not for
 * the (slightly longer) encrypted record, without ever having to be replaced because the
 * {@link javax.net.ssl.SSLEngine} reported {@code BUFFER_OVERFLOW}.
 */
public class SslHandlerUnwrapBufferSizeTest {

    private static final int MAX_PLAINTEXT_LENGTH = 16 * 1024;
    private static final int RECORDS = 5;

    private static final String[] TLS_V1_2_CIPHERS = {
            null,
            "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",
            "TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256"
    };

    private static final String[] TLS_V1_3_CIPHERS = {
            null,
            "TLS_AES_256_GCM_SHA384",
            "TLS_CHACHA20_POLY1305_SHA256"
    };

    /** How many bytes of the data are delivered to the receiver at once, {@code 0} for one record at a time. */
    private static final int[] READ_SIZES = { 0, 8 * 1024, 20 * 1024, 64 * 1024 };

    /** The {@link javax.net.ssl.SSLEngine} implementation of the {@link SslHandler} that receives the data. */
    private enum Engine {
        JDK(SslProvider.JDK),
        /** {@code SslContext.newHandler(...)}, which uses the OpenSSL engine in non jdkCompatibilityMode. */
        OPENSSL(SslProvider.OPENSSL),
        /** {@code new SslHandler(SslContext.newEngine(...))}, which uses the OpenSSL engine in jdkCompatibilityMode. */
        OPENSSL_NEW_ENGINE(SslProvider.OPENSSL),
        /** Not handled specifically by {@link SslHandler}, so it is treated like the JDK engine. */
        BOUNCY_CASTLE(SslProvider.JDK),
        /** Only available on Java 14 and earlier. */
        CONSCRYPT(SslProvider.JDK);

        final SslProvider provider;

        Engine(SslProvider provider) {
            this.provider = provider;
        }

        boolean isOpenSsl() {
            return provider == SslProvider.OPENSSL;
        }

        /** Whether the first buffer for a full record is as small as the plaintext. */
        boolean needsOnlyThePlaintext() {
            return isOpenSsl() || this == CONSCRYPT;
        }
    }

    static List<Object[]> data() {
        List<Object[]> params = new ArrayList<Object[]>();
        for (Engine receiver : Engine.values()) {
            for (Engine sender : new Engine[] { Engine.JDK, Engine.OPENSSL }) {
                for (String protocol : new String[] { SslProtocols.TLS_v1_3, SslProtocols.TLS_v1_2 }) {
                    if (receiver == Engine.CONSCRYPT && SslProtocols.TLS_v1_3.equals(protocol)) {
                        continue;
                    }
                    String[] ciphers = SslProtocols.TLS_v1_3.equals(protocol) ? TLS_V1_3_CIPHERS : TLS_V1_2_CIPHERS;
                    for (String cipher : ciphers) {
                        for (int readSize : READ_SIZES) {
                            if (readSize != 0 && receiver != Engine.OPENSSL) {
                                // Only the OpenSSL engine in non jdkCompatibilityMode is handed more than one
                                // record at a time, all others wait for a full record.
                                continue;
                            }
                            params.add(new Object[] { receiver, sender, protocol, cipher, readSize });
                        }
                    }
                }
            }
        }
        return params;
    }

    /**
     * The JDK engine is given {@code length - 21} bytes for a record, which relies on none of the cipher suites it
     * supports needing more space than that. Check this for all RSA cipher suites it and BouncyCastle enable by
     * default, so a suite that is added to a JDK later on that does is noticed.
     */
    static List<Object[]> enabledCipherSuites() throws Exception {
        List<Object[]> params = new ArrayList<Object[]>();
        for (Engine receiver : new Engine[] { Engine.JDK, Engine.BOUNCY_CASTLE }) {
            SSLEngine sslEngine;
            if (receiver == Engine.BOUNCY_CASTLE) {
                SSLContext context = SSLContext.getInstance("TLS", newBouncyCastleJsseProvider());
                context.init(null, null, null);
                sslEngine = context.createSSLEngine();
            } else {
                sslEngine = SSLContext.getDefault().createSSLEngine();
            }
            for (Engine sender : new Engine[] { Engine.JDK, Engine.OPENSSL }) {
                for (String protocol : new String[] { SslProtocols.TLS_v1_3, SslProtocols.TLS_v1_2 }) {
                    for (String cipher : sslEngine.getEnabledCipherSuites()) {
                        boolean tls13 = cipher.startsWith("TLS_AES_") || cipher.startsWith("TLS_CHACHA20_");
                        // The self signed certificate is an RSA one, so only the suites that can use it can be tested.
                        // The static ECDH ones (TLS_ECDH_RSA_*) need an EC key instead.
                        if (SslProtocols.TLS_v1_3.equals(protocol) ? tls13 :
                                !tls13 && cipher.contains("_RSA_") && !cipher.contains("_ECDH_RSA_")) {
                            params.add(new Object[] { receiver, sender, protocol, cipher });
                        }
                    }
                }
            }
        }
        return params;
    }

    @ParameterizedTest(name = "{index}: receiver={0}, sender={1}, {2}, cipher={3}, readSize={4}")
    @Timeout(30)
    @MethodSource("data")
    public void testFullRecordsAreUnwrappedIntoBuffersSizedForThePlaintext(
            Engine receiver, Engine sender, String protocol, String cipher, int readSize) throws Exception {
        unwrapFullRecords(receiver, sender, protocol, cipher, readSize, false);
    }

    @ParameterizedTest(name = "{index}: receiver={0}, sender={1}, {2}, cipher={3}")
    @Timeout(30)
    @MethodSource("enabledCipherSuites")
    public void testEnabledCipherSuitesNeedNoBiggerBufferThanTheFirstOne(
            Engine receiver, Engine sender, String protocol, String cipher) throws Exception {
        // The suites come from what the engines enable, which differs between JDKs and may include some that can not
        // be used with the test certificate. They are skipped, as it is not what is tested here.
        unwrapFullRecords(receiver, sender, protocol, cipher, 0, true);
    }

    private static void unwrapFullRecords(Engine receiver, Engine sender, String protocol, String cipher,
                                          int readSize, boolean skipIfHandshakeFails) throws Exception {
        assumeTrue(!receiver.isOpenSsl() && !sender.isOpenSsl() || OpenSsl.isAvailable(),
                "OpenSSL is not available");
        assumeTrue(receiver != Engine.CONSCRYPT || Conscrypt.isAvailable(), "Conscrypt is not available");
        if (SslProtocols.TLS_v1_3.equals(protocol)) {
            // Ensure we support TLSv1.3
            assumeTrue(SslProvider.isTlsv13Supported(receiver.provider) &&
                    SslProvider.isTlsv13Supported(sender.provider));
        }
        assumeTrue(cipher == null || isSupported(receiver, cipher) && isSupported(sender, cipher),
                cipher + " is not supported");

        SslContext serverContext = newContext(receiver, true, protocol, cipher);
        SslContext clientContext = newContext(sender, false, protocol, cipher);
        AllocationRecordingAllocator recording = new AllocationRecordingAllocator();
        EmbeddedChannel server = new EmbeddedChannel();
        server.config().setAllocator(recording);
        server.pipeline().addLast(receiver == Engine.OPENSSL_NEW_ENGINE ?
                new SslHandler(serverContext.newEngine(UnpooledByteBufAllocator.DEFAULT)) :
                serverContext.newHandler(UnpooledByteBufAllocator.DEFAULT));
        EmbeddedChannel client = new EmbeddedChannel(clientContext.newHandler(UnpooledByteBufAllocator.DEFAULT));
        try {
            Future<Channel> serverHandshake = server.pipeline().get(SslHandler.class).handshakeFuture();
            Future<Channel> clientHandshake = client.pipeline().get(SslHandler.class).handshakeFuture();
            try {
                for (int i = 0; i < 100 && !(serverHandshake.isDone() && clientHandshake.isDone()); i++) {
                    transfer(client, server, 0);
                    transfer(server, client, 0);
                }
            } catch (DecoderException e) {
                assumeTrue(!skipIfHandshakeFails, cipher + " is not usable with the test certificate: " + e);
                throw e;
            }
            assumeTrue(!skipIfHandshakeFails || serverHandshake.isSuccess() && clientHandshake.isSuccess(),
                    cipher + " is not usable with the test certificate");
            assertTrue(serverHandshake.isSuccess(), String.valueOf(serverHandshake.cause()));
            assertTrue(clientHandshake.isSuccess(), String.valueOf(clientHandshake.cause()));
            recording.capacities.clear();

            byte[] data = new byte[RECORDS * MAX_PLAINTEXT_LENGTH];
            for (int i = 0; i < data.length; i++) {
                data[i] = (byte) i;
            }
            client.writeAndFlush(Unpooled.wrappedBuffer(data));
            List<Integer> wireRecords = transfer(client, server, readSize);

            int maxCapacity = maxExpectedCapacity(receiver, Collections.max(wireRecords));
            int received = 0;
            ByteBuf in;
            while ((in = server.readInbound()) != null) {
                try {
                    assertThat(in.capacity()).isLessThanOrEqualTo(maxCapacity);
                    for (int i = 0; i < in.readableBytes(); i++) {
                        assertEquals(data[received + i], in.getByte(in.readerIndex() + i));
                    }
                    received += in.readableBytes();
                } finally {
                    in.release();
                }
            }
            assertEquals(data.length, received);

            if (readSize == 0) {
                // Every record is unwrapped into the first buffer that was allocated for it: a smaller buffer than
                // the SSLEngine needs would show up as an additional allocation after BUFFER_OVERFLOW.
                assertEquals(wireRecords.size(), recording.capacities.size(),
                        "unwrap allocations: " + recording.capacities);
            }
        } finally {
            client.finishAndReleaseAll();
            server.finishAndReleaseAll();
            ReferenceCountUtil.release(clientContext);
            ReferenceCountUtil.release(serverContext);
        }
    }

    /**
     * The OpenSSL and Conscrypt engines never need more than the plaintext. The JDK engine wants all but the header
     * and the tag / MAC of a record, which is a bit more for TLSv1.2 AES-GCM and CBC records, and for TLSv1.3 records
     * that are filled up to the maximum by a peer that is not using the JDK.
     */
    private static int maxExpectedCapacity(Engine receiver, int maxRecordLength) {
        return receiver.needsOnlyThePlaintext() ? MAX_PLAINTEXT_LENGTH :
                Math.max(MAX_PLAINTEXT_LENGTH, maxRecordLength - 5 - 16);
    }

    private static SslContext newContext(Engine engine, boolean server, String protocol, String cipher)
            throws Exception {
        SslContextBuilder builder;
        if (server) {
            SelfSignedCertificate cert = CachedSelfSignedCertificate.getCachedCertificate();
            builder = SslContextBuilder.forServer(cert.certificate(), cert.privateKey());
        } else {
            builder = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE);
        }
        builder.sslProvider(engine.provider).protocols(protocol);
        if (cipher != null) {
            builder.ciphers(Collections.singletonList(cipher), SupportedCipherSuiteFilter.INSTANCE);
        }
        if (engine == Engine.BOUNCY_CASTLE) {
            builder.sslContextProvider(newBouncyCastleJsseProvider());
        } else if (engine == Engine.CONSCRYPT) {
            // Conscrypt is only used by SslHandler as a ConscryptAlpnSslEngine.
            builder.sslContextProvider(Java8SslTestUtils.conscryptProvider()).applicationProtocolConfig(
                    new ApplicationProtocolConfig(ApplicationProtocolConfig.Protocol.ALPN,
                            ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                            ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT, "http/1.1"));
        }
        return builder.build();
    }

    private static boolean isSupported(Engine engine, String cipher) throws Exception {
        if (engine.isOpenSsl()) {
            return OpenSsl.availableJavaCipherSuites().contains(cipher);
        }
        SSLEngine sslEngine;
        if (engine == Engine.BOUNCY_CASTLE || engine == Engine.CONSCRYPT) {
            SSLContext context = SSLContext.getInstance("TLS", engine == Engine.CONSCRYPT ?
                    Java8SslTestUtils.conscryptProvider() : newBouncyCastleJsseProvider());
            context.init(null, null, null);
            sslEngine = context.createSSLEngine();
        } else {
            sslEngine = SSLContext.getDefault().createSSLEngine();
        }
        return Arrays.asList(sslEngine.getSupportedCipherSuites()).contains(cipher);
    }

    private static Provider newBouncyCastleJsseProvider() {
        return new BouncyCastleJsseProvider(false, new BouncyCastleProvider());
    }

    /**
     * Moves all outbound data of {@code from} to the inbound of {@code to}, in reads of {@code readSize} bytes unless
     * it is {@code 0}, in which case each outbound buffer is delivered as is. Returns the length of every TLS record
     * that was moved.
     */
    private static List<Integer> transfer(EmbeddedChannel from, EmbeddedChannel to, int readSize) {
        List<Integer> records = new ArrayList<Integer>();
        ByteBuf all = readSize == 0 ? null : Unpooled.buffer();
        ByteBuf buf;
        while ((buf = from.readOutbound()) != null) {
            int i = buf.readerIndex();
            while (i + 5 <= buf.writerIndex()) {
                int length = 5 + buf.getUnsignedShort(i + 3);
                records.add(length);
                i += length;
            }
            if (all == null) {
                to.writeInbound(buf);
            } else {
                all.writeBytes(buf);
                buf.release();
            }
        }
        if (all != null) {
            while (all.isReadable()) {
                to.writeInbound(all.readRetainedSlice(Math.min(readSize, all.readableBytes())));
            }
            all.release();
        }
        return records;
    }

    private static final class AllocationRecordingAllocator extends AbstractByteBufAllocator {
        final List<Integer> capacities = new ArrayList<Integer>();

        AllocationRecordingAllocator() {
            super(false);
        }

        @Override
        protected ByteBuf newHeapBuffer(int initialCapacity, int maxCapacity) {
            capacities.add(initialCapacity);
            return UnpooledByteBufAllocator.DEFAULT.heapBuffer(initialCapacity, maxCapacity);
        }

        @Override
        protected ByteBuf newDirectBuffer(int initialCapacity, int maxCapacity) {
            capacities.add(initialCapacity);
            return UnpooledByteBufAllocator.DEFAULT.directBuffer(initialCapacity, maxCapacity);
        }

        @Override
        public boolean isDirectBufferPooled() {
            return false;
        }
    }
}
