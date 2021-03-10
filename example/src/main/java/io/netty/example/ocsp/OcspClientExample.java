/*
 * Copyright 2017 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.example.ocsp;

import java.math.BigInteger;

import javax.net.ssl.SSLSession;
import javax.security.cert.X509Certificate;

import io.netty.buffer.Unpooled;
import org.bouncycastle.asn1.ocsp.OCSPResponseStatus;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.SingleResp;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.ReferenceCountedOpenSslContext;
import io.netty.handler.ssl.ReferenceCountedOpenSslEngine;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.ocsp.OcspClientHandler;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Promise;

/**
 * This is a very simple example for an HTTPS client that uses OCSP stapling.
 * The client connects to an HTTPS server that has OCSP stapling enabled and
 * then uses BC to parse and validate it.
 */
public class OcspClientExample {
    public static void main(String[] args) throws Exception {
        if (!OpenSsl.isAvailable()) {
            throw new IllegalStateException("OpenSSL is not available!");
        }

        if (!OpenSsl.isOcspSupported()) {
            throw new IllegalStateException("OCSP is not supported!");
        }

        // Using Wikipedia as an example. I'd rather use Netty's own website
        // but the server (Cloudflare) doesn't support OCSP stapling. A few
        // other examples could be Microsoft or Squarespace. Use OpenSSL's
        // CLI client to assess if a server supports OCSP stapling. E.g.:
        //
        // openssl s_client -tlsextdebug -status -connect www.squarespace.com:443
        //
        String host = "www.wikipedia.org";

        ReferenceCountedOpenSslContext context
            = (ReferenceCountedOpenSslContext) SslContextBuilder.forClient()
                .sslProvider(SslProvider.OPENSSL)
                .enableOcsp(true)
                .build();

        try {
            EventLoopGroup group = new NioEventLoopGroup();
            try {
                Promise<FullHttpResponse> promise = group.next().newPromise();

                Bootstrap bootstrap = new Bootstrap()
                        .channel(NioSocketChannel.class)
                        .group(group)
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5 * 1000)
                        .handler(newClientHandler(context, host, promise));

                Channel channel = bootstrap.connect(host, 443)
                        .syncUninterruptibly()
                        .channel();

                try {
                    FullHttpResponse response = promise.get();
                    ReferenceCountUtil.release(response);
                } finally {
                    channel.close();
                }
            } finally {
                group.shutdownGracefully();
            }
        } finally {
            context.release();
        }
    }

    private static ChannelInitializer<Channel> newClientHandler(final ReferenceCountedOpenSslContext context,
            final String host, final Promise<FullHttpResponse> promise) {

        return new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                SslHandler sslHandler = context.newHandler(ch.alloc());
                ReferenceCountedOpenSslEngine engine
                    = (ReferenceCountedOpenSslEngine) sslHandler.engine();

                ChannelPipeline pipeline = ch.pipeline();
                pipeline.addLast(sslHandler);
                pipeline.addLast(new ExampleOcspClientHandler(engine));

                pipeline.addLast(new HttpClientCodec());
                pipeline.addLast(new HttpObjectAggregator(1024 * 1024));
                pipeline.addLast(new HttpClientHandler(host, promise));
            }

            @Override
            public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                if (!promise.isDone()) {
                    promise.tryFailure(new IllegalStateException("Connection closed and Promise was not done."));
                }
                ctx.fireChannelInactive();
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                if (!promise.tryFailure(cause)) {
                    ctx.fireExceptionCaught(cause);
                }
            }
        };
    }

    private static class HttpClientHandler extends ChannelInboundHandlerAdapter {

        private final String host;

        private final Promise<FullHttpResponse> promise;

        HttpClientHandler(String host, Promise<FullHttpResponse> promise) {
            this.host = host;
            this.promise = promise;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            FullHttpRequest request = new DefaultFullHttpRequest(
                    HttpVersion.HTTP_1_1, HttpMethod.GET, "/", Unpooled.EMPTY_BUFFER);
            request.headers().set(HttpHeaderNames.HOST, host);
            request.headers().set(HttpHeaderNames.USER_AGENT, "netty-ocsp-example/1.0");

            ctx.writeAndFlush(request).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);

            ctx.fireChannelActive();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            if (!promise.isDone()) {
                promise.tryFailure(new IllegalStateException("Connection closed and Promise was not done."));
            }
            ctx.fireChannelInactive();
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof FullHttpResponse) {
                if (!promise.trySuccess((FullHttpResponse) msg)) {
                    ReferenceCountUtil.release(msg);
                }
                return;
            }

            ctx.fireChannelRead(msg);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            if (!promise.tryFailure(cause)) {
                ctx.fireExceptionCaught(cause);
            }
        }
    }

    private static class ExampleOcspClientHandler extends OcspClientHandler {

        ExampleOcspClientHandler(ReferenceCountedOpenSslEngine engine) {
            super(engine);
        }

        @Override
        protected boolean verify(ChannelHandlerContext ctx, ReferenceCountedOpenSslEngine engine) throws Exception {
            byte[] staple = engine.getOcspResponse();
            if (staple == null) {
                throw new IllegalStateException("Server didn't provide an OCSP staple!");
            }

            OCSPResp response = new OCSPResp(staple);
            if (response.getStatus() != OCSPResponseStatus.SUCCESSFUL) {
                return false;
            }

            SSLSession session = engine.getSession();
            X509Certificate[] chain = session.getPeerCertificateChain();
            BigInteger certSerial = chain[0].getSerialNumber();

            BasicOCSPResp basicResponse = (BasicOCSPResp) response.getResponseObject();
            SingleResp first = basicResponse.getResponses()[0];

            // ATTENTION: CertificateStatus.GOOD is actually a null value! Do not use
            // equals() or you'll NPE!
            CertificateStatus status = first.getCertStatus();
            BigInteger ocspSerial = first.getCertID().getSerialNumber();
            String message = new StringBuilder()
                .append("OCSP status of ").append(ctx.channel().remoteAddress())
                .append("\n  Status: ").append(status == CertificateStatus.GOOD ? "Good" : status)
                .append("\n  This Update: ").append(first.getThisUpdate())
                .append("\n  Next Update: ").append(first.getNextUpdate())
                .append("\n  Cert Serial: ").append(certSerial)
                .append("\n  OCSP Serial: ").append(ocspSerial)
                .toString();
            System.out.println(message);

            return status == CertificateStatus.GOOD && certSerial.equals(ocspSerial);
        }
    }
}
