/*
 * Copyright 2010 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.example.portunification;

import javax.net.ssl.SSLEngine;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.example.factorial.BigIntegerDecoder;
import org.jboss.netty.example.factorial.FactorialServerHandler;
import org.jboss.netty.example.factorial.NumberEncoder;
import org.jboss.netty.example.http.snoop.HttpRequestHandler;
import org.jboss.netty.example.securechat.SecureChatSslContextFactory;
import org.jboss.netty.handler.codec.compression.ZlibDecoder;
import org.jboss.netty.handler.codec.compression.ZlibEncoder;
import org.jboss.netty.handler.codec.compression.ZlibWrapper;
import org.jboss.netty.handler.codec.frame.FrameDecoder;
import org.jboss.netty.handler.codec.http.HttpContentCompressor;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.ssl.SslHandler;

/**
 * Manipulates the current pipeline dynamically to switch protocols or enable
 * SSL or GZIP.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2129 $, $Date: 2010-02-02 13:31:12 +0900 (Tue, 02 Feb 2010) $
 */
public class PortUnificationServerHandler extends FrameDecoder {

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
    protected Object decode(ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer) throws Exception {

        // Will use the first two bytes to detect a protocol.
        if (buffer.readableBytes() < 2) {
            return null;
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
            buffer.skipBytes(buffer.readableBytes());
            ctx.getChannel().close();
            return null;
        }

        // Forward the current read buffer as is to the new handlers.
        return buffer.readBytes(buffer.readableBytes());
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

    private boolean isHttp(int magic1, int magic2) {
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

    private boolean isFactorial(int magic1) {
        return magic1 == 'F';
    }

    private void enableSsl(ChannelHandlerContext ctx) {
        ChannelPipeline p = ctx.getPipeline();

        SSLEngine engine =
            SecureChatSslContextFactory.getServerContext().createSSLEngine();
        engine.setUseClientMode(false);

        p.addLast("ssl", new SslHandler(engine));
        p.addLast("unificationA", new PortUnificationServerHandler(false, detectGzip));
        p.remove(this);
    }

    private void enableGzip(ChannelHandlerContext ctx) {
        ChannelPipeline p = ctx.getPipeline();
        p.addLast("gzipdeflater", new ZlibEncoder(ZlibWrapper.GZIP));
        p.addLast("gzipinflater", new ZlibDecoder(ZlibWrapper.GZIP));
        p.addLast("unificationB", new PortUnificationServerHandler(detectSsl, false));
        p.remove(this);
    }

    private void switchToHttp(ChannelHandlerContext ctx) {
        ChannelPipeline p = ctx.getPipeline();
        p.addLast("decoder", new HttpRequestDecoder());
        p.addLast("encoder", new HttpResponseEncoder());
        p.addLast("deflater", new HttpContentCompressor());
        p.addLast("handler", new HttpRequestHandler());
        p.remove(this);
    }

    private void switchToFactorial(ChannelHandlerContext ctx) {
        ChannelPipeline p = ctx.getPipeline();
        p.addLast("decoder", new BigIntegerDecoder());
        p.addLast("encoder", new NumberEncoder());
        p.addLast("handler", new FactorialServerHandler());
        p.remove(this);
    }
}
