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

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.quic.InsecureQuicTokenHandler;
import io.netty.handler.codec.quic.Quic;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicClientCodecBuilder;
import io.netty.handler.codec.quic.QuicServerCodecBuilder;
import io.netty.handler.codec.quic.QuicSslContext;
import io.netty.handler.codec.quic.QuicSslContextBuilder;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.handler.codec.quic.QuicStreamType;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.CharsetUtil;

import javax.net.ssl.KeyManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Checks that a Linux {@code libnetty_quiche*.so} built against glibc can be loaded, initialized
 * and actually <em>used</em> by the libc of the machine running this class. Written for
 * Alpine/musl, with a glibc run as the control.
 *
 * <p>The handshake level is the point of this class. musl does not fail a dlopen on a relocation
 * it cannot resolve -- it defers it and aborts at the first call instead -- so "the library
 * loaded" says very little about a library that carries a whole Rust standard library inside it.
 * The undefined glibc-only symbols that motivated {@code musl_compat.c} (the LFS64 family,
 * {@code __xstat64} and friends) sit in quiche's file and socket paths, which nothing before this
 * level touches. A run that stops at {@code init} would have reported PASS for every artifact
 * netty has ever released.
 *
 * <p>One level per JVM, chosen by the first argument, so a hard crash in an early level cannot
 * hide a later one. The historical aarch64 failure in netty-tcnative was a SIGSEGV inside
 * {@code JVM_LoadLibrary} rather than an exception, so the caller must also treat a JVM that dies
 * without printing a RESULT line as a failure -- see verify.sh.
 *
 * <p>Client and server both run in this one JVM over the loopback interface: a QUIC handshake
 * needs two peers, and putting them in two containers would test the container network rather
 * than the library.
 *
 * <p>Deliberately kept to Java 8 syntax and to the netty artifacts themselves. The server
 * certificate comes in as a PKCS#12 keystore built by keytool rather than from
 * {@code SelfSignedCertificate}, which needs either a BouncyCastle jar or
 * {@code --add-exports java.base/sun.security.x509} and would make the check fail for reasons
 * that have nothing to do with musl.
 */
public final class QuicMuslCheck {

    private static final String ALPN = "netty-musl-check";
    private static final String PAYLOAD = "PING";
    private static final long TIMEOUT_SECONDS = 30;

    private static boolean failed;

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("usage: QuicMuslCheck <load|init|handshake> <path-to-.so> [keystore.p12] [password]");
            System.exit(2);
        }
        String level = args[0];
        String soPath = args[1];

        try {
            // Every level needs the library loaded; the load itself is level "load".
            load(soPath);
            if (!"load".equals(level)) {
                init();
                if ("handshake".equals(level)) {
                    if (args.length < 4) {
                        throw new IllegalStateException("the handshake level needs a keystore and a password");
                    }
                    handshake(args[2], args[3]);
                }
            }
        } catch (Throwable t) {
            // A missing symbol surfaces here as the musl loader's own text, e.g.
            // "Error relocating <lib>: __xstat64: symbol not found".
            result(level, false, rootMessage(t));
        }
        System.exit(failed ? 1 : 0);
    }

    /**
     * Loads the .so straight off disk, before netty gets a chance to unpack its own copy. This is
     * the level that catches an unresolvable DT_NEEDED, which on musl is a hard dlopen failure
     * rather than a deferred one.
     *
     * <p>Not a pure dlopen, and cannot be made into one: the library's {@code JNI_OnLoad} calls
     * FindClass, so a successful dlopen continues straight into netty's own class and library
     * loading. Reading a failure here therefore means looking at which file the message names --
     * this one, or the temporary copy {@code NativeLibraryLoader} unpacks out of the jar.
     */
    private static void load(String soPath) {
        File so = new File(soPath);
        if (!so.isFile()) {
            throw new IllegalStateException("not a file: " + soPath);
        }
        System.load(so.getAbsolutePath());
        result("load", true, "System.load ok: " + so.getName());
    }

    private static void init() {
        // Loads netty's own copy out of the jar and runs the static initializer, which calls into
        // quiche for the version string.
        Quic.ensureAvailability();
        result("init", true, "Quic.isAvailable()=" + Quic.isAvailable());
    }

    private static void handshake(String keystorePath, String password) throws Exception {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        Channel serverChannel = null;
        Channel clientChannel = null;
        try {
            InetAddress loopback = InetAddress.getLoopbackAddress();
            serverChannel = new Bootstrap()
                    .group(group)
                    .channel(NioDatagramChannel.class)
                    .handler(newServerCodec(keystorePath, password))
                    .bind(new InetSocketAddress(loopback, 0)).sync().channel();
            int port = ((InetSocketAddress) serverChannel.localAddress()).getPort();
            result("handshake", true, "server bound on " + loopback.getHostAddress() + ':' + port);

            QuicSslContext clientSslContext = QuicSslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .applicationProtocols(ALPN)
                    .build();
            ChannelHandler clientCodec = new QuicClientCodecBuilder()
                    .sslContext(clientSslContext)
                    .maxIdleTimeout(5000, TimeUnit.MILLISECONDS)
                    .initialMaxData(1000000)
                    .initialMaxStreamDataBidirectionalLocal(100000)
                    .build();
            clientChannel = new Bootstrap()
                    .group(group)
                    .channel(NioDatagramChannel.class)
                    .handler(clientCodec)
                    .bind(new InetSocketAddress(loopback, 0)).sync().channel();

            QuicChannel quicChannel = QuicChannel.newBootstrap(clientChannel)
                    .streamHandler(new ChannelInboundHandlerAdapter())
                    .remoteAddress(new InetSocketAddress(loopback, port))
                    .connect()
                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            result("handshake", true, "connected, alpn=" + ALPN);

            // A queue rather than a latch plus a field: it carries the echoed bytes and the
            // "stream closed with nothing" case in one place, and poll() gives the timeout.
            LinkedBlockingQueue<String> echoed = new LinkedBlockingQueue<String>();
            QuicStreamChannel stream = quicChannel.createStream(
                    QuicStreamType.BIDIRECTIONAL, new EchoCollector(echoed)).sync().getNow();
            stream.writeAndFlush(Unpooled.copiedBuffer(PAYLOAD, CharsetUtil.US_ASCII))
                    .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);

            String received = echoed.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (received == null) {
                throw new IllegalStateException("no echo within " + TIMEOUT_SECONDS + "s");
            }
            if (!PAYLOAD.equals(received)) {
                throw new IllegalStateException("echo mismatch: expected '" + PAYLOAD
                        + "' but got '" + received + '\'');
            }
            result("handshake", true, "bidirectional stream echoed " + received.length() + " bytes");

            quicChannel.close().sync();
        } finally {
            if (clientChannel != null) {
                clientChannel.close().sync();
            }
            if (serverChannel != null) {
                serverChannel.close().sync();
            }
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS).sync();
        }
    }

    private static ChannelHandler newServerCodec(String keystorePath, String password) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        InputStream in = new FileInputStream(keystorePath);
        try {
            keyStore.load(in, password.toCharArray());
        } finally {
            in.close();
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, password.toCharArray());

        QuicSslContext sslContext = QuicSslContextBuilder.forServer(kmf, password)
                .applicationProtocols(ALPN)
                .build();
        return new QuicServerCodecBuilder()
                .sslContext(sslContext)
                .maxIdleTimeout(5000, TimeUnit.MILLISECONDS)
                .initialMaxData(1000000)
                .initialMaxStreamDataBidirectionalLocal(100000)
                .initialMaxStreamDataBidirectionalRemote(100000)
                .initialMaxStreamsBidirectional(10)
                .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
                .handler(new SharableNoopHandler())
                .streamHandler(new ChannelInitializer<QuicStreamChannel>() {
                    @Override
                    protected void initChannel(QuicStreamChannel ch) {
                        ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                // Echo straight back and send the FIN with it. The payload is a
                                // handful of bytes, so it cannot arrive split.
                                ctx.writeAndFlush(msg).addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);
                            }
                        });
                    }
                })
                .build();
    }

    /** Collects the echoed bytes and hands them over once the peer has sent its FIN. */
    private static final class EchoCollector extends ChannelInboundHandlerAdapter {

        private final LinkedBlockingQueue<String> out;
        private final StringBuilder received = new StringBuilder();

        EchoCollector(LinkedBlockingQueue<String> out) {
            this.out = out;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ByteBuf buf = (ByteBuf) msg;
            try {
                received.append(buf.toString(CharsetUtil.US_ASCII));
            } finally {
                buf.release();
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            out.offer(received.toString());
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            out.offer("exception: " + cause);
            ctx.close();
        }
    }

    /** The server needs one connection-level handler instance across every connection. */
    private static final class SharableNoopHandler extends ChannelInboundHandlerAdapter {
        @Override
        public boolean isSharable() {
            return true;
        }
    }

    private static void result(String level, boolean pass, String detail) {
        if (!pass) {
            failed = true;
        }
        System.out.println("RESULT\t" + level + '\t' + (pass ? "PASS" : "FAIL") + '\t' + detail);
    }

    /** Walks to the deepest cause and flattens it onto one line. */
    private static String rootMessage(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String msg = root.getMessage();
        if (msg == null || msg.isEmpty()) {
            msg = root.toString();
        }
        return root.getClass().getSimpleName() + ": " + msg.replace('\n', ' ').replace('\r', ' ');
    }

    private QuicMuslCheck() {
    }
}
