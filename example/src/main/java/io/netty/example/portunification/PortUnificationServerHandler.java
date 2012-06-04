/*
 * Copyright 2012 The Netty Project
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
package io.netty.example.portunification;

import io.netty.buffer.ChannelBuffer;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerContext;
import io.netty.channel.ChannelInboundStreamHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.example.factorial.BigIntegerDecoder;
import io.netty.example.factorial.FactorialServerHandler;
import io.netty.example.factorial.NumberEncoder;
import io.netty.example.http.snoop.HttpSnoopServerHandler;
import io.netty.example.securechat.SecureChatSslContextFactory;
import io.netty.handler.codec.compression.ZlibDecoder;
import io.netty.handler.codec.compression.ZlibEncoder;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.ssl.SslHandler;

import javax.net.ssl.SSLEngine;

/**
 * Manipulates the current pipeline dynamically to switch protocols or enable
 * SSL or GZIP.
 */
public class PortUnificationServerHandler extends ChannelInboundStreamHandlerAdapter {

    private final boolean detectSsl;
    private final boolean detectGzip;

    public PortUnificationServerHandler() {
        this(true, true);
    }

    private PortUnificationServerHandler(boolean detectSsl, boolean detectGzip) {
        this.detectSsl = detectSsl;
        this.detectGzip = detectGzip;
    }

    @Override
    public void inboundBufferUpdated(ChannelInboundHandlerContext<Byte> ctx) throws Exception {
        ChannelBuffer buffer = ctx.inbound().byteBuffer();

        // Will use the first two bytes to detect a protocol.
        if (buffer.readableBytes() < 2) {
            return;
        }

        final int magic1 = buffer.getUnsignedByte(buffer.readerIndex());
        final int magic2 = buffer.getUnsignedByte(buffer.readerIndex() + 1);

        if (isSsl(magic1)) {
            enableSsl(ctx);
        } else if (isGzip(magic1, magic2)) {
            enableGzip(ctx);
        } else if (isHttp(magic1, magic2)) {
            switchToHttp(ctx);
        } else if (isFactorial(magic1)) {
            switchToFactorial(ctx);
        } else {
            // Unknown protocol; discard everything and close the connection.
            buffer.clear();
            ctx.close();
            return;
        }

        // Forward the current read buffer as is to the new handlers.
        ctx.nextInboundByteBuffer().writeBytes(buffer);
        ctx.fireInboundBufferUpdated();
    }

    private boolean isSsl(int magic1) {
        if (detectSsl) {
            switch (magic1) {
            case 20: case 21: case 22: case 23: case 255:
                return true;
            default:
                return magic1 >= 128;
            }
        }
        return false;
    }

    private boolean isGzip(int magic1, int magic2) {
        if (detectGzip) {
            return magic1 == 31 && magic2 == 139;
        }
        return false;
    }

    private static boolean isHttp(int magic1, int magic2) {
        return
            magic1 == 'G' && magic2 == 'E' || // GET
            magic1 == 'P' && magic2 == 'O' || // POST
            magic1 == 'P' && magic2 == 'U' || // PUT
            magic1 == 'H' && magic2 == 'E' || // HEAD
            magic1 == 'O' && magic2 == 'P' || // OPTIONS
            magic1 == 'P' && magic2 == 'A' || // PATCH
            magic1 == 'D' && magic2 == 'E' || // DELETE
            magic1 == 'T' && magic2 == 'R' || // TRACE
            magic1 == 'C' && magic2 == 'O';   // CONNECT
    }

    private static boolean isFactorial(int magic1) {
        return magic1 == 'F';
    }

    private void enableSsl(ChannelHandlerContext ctx) {
        ChannelPipeline p = ctx.pipeline();

        SSLEngine engine =
            SecureChatSslContextFactory.getServerContext().createSSLEngine();
        engine.setUseClientMode(false);

        p.addLast("ssl", new SslHandler(engine));
        p.addLast("unificationA", new PortUnificationServerHandler(false, detectGzip));
        p.remove(this);
    }

    private void enableGzip(ChannelHandlerContext ctx) {
        ChannelPipeline p = ctx.pipeline();
        p.addLast("gzipdeflater", new ZlibEncoder(ZlibWrapper.GZIP));
        p.addLast("gzipinflater", new ZlibDecoder(ZlibWrapper.GZIP));
        p.addLast("unificationB", new PortUnificationServerHandler(detectSsl, false));
        p.remove(this);
    }

    private void switchToHttp(ChannelHandlerContext ctx) {
        ChannelPipeline p = ctx.pipeline();
        p.addLast("decoder", new HttpRequestDecoder());
        p.addLast("encoder", new HttpResponseEncoder());
        p.addLast("deflater", new HttpContentCompressor());
        p.addLast("handler", new HttpSnoopServerHandler());
        p.remove(this);
    }

    private void switchToFactorial(ChannelHandlerContext ctx) {
        ChannelPipeline p = ctx.pipeline();
        p.addLast("decoder", new BigIntegerDecoder());
        p.addLast("encoder", new NumberEncoder());
        p.addLast("handler", new FactorialServerHandler());
        p.remove(this);
    }
}
