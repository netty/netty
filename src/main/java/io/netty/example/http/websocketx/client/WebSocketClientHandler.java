//The MIT License
//
//Copyright (c) 2009 Carl Bystršm
//
//Permission is hereby granted, free of charge, to any person obtaining a copy
//of this software and associated documentation files (the "Software"), to deal
//in the Software without restriction, including without limitation the rights
//to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
//copies of the Software, and to permit persons to whom the Software is
//furnished to do so, subject to the following conditions:
//
//The above copyright notice and this permission notice shall be included in
//all copies or substantial portions of the Software.
//
//THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
//IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
//FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
//AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
//LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
//OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
//THE SOFTWARE.

package io.netty.example.http.websocketx.client;

import java.net.InetSocketAddress;
import java.net.URI;

import io.netty.bootstrap.ClientBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelStateEvent;
import io.netty.channel.ExceptionEvent;
import io.netty.channel.MessageEvent;
import io.netty.channel.SimpleChannelUpstreamHandler;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketSpecificationVersion;
import io.netty.util.CharsetUtil;

/**
 * Copied from https://github.com/cgbystrom/netty-tools
 * 
 * Handles socket communication for a connected WebSocket client Not intended for end-users. Please use
 * {@link WebSocketClient} or {@link WebSocketCallback} for controlling your client.
 * 
 * @author <a href="http://www.pedantique.org/">Carl Bystr&ouml;m</a>
 * @author <a href="http://www.veebsbraindump.com/">Vibul Imtarnasan</a>
 */
public class WebSocketClientHandler extends SimpleChannelUpstreamHandler implements WebSocketClient {

    private final ClientBootstrap bootstrap;
    private URI url;
    private final WebSocketCallback callback;
    private Channel channel;
    private WebSocketClientHandshaker handshaker = null;
    private final WebSocketSpecificationVersion version;

    public WebSocketClientHandler(ClientBootstrap bootstrap, URI url, WebSocketSpecificationVersion version, WebSocketCallback callback) {
        this.bootstrap = bootstrap;
        this.url = url;
        this.version = version;
        this.callback = callback;
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        channel = e.getChannel();
        this.handshaker = new WebSocketClientHandshakerFactory().newHandshaker(url, version, null, false);
        handshaker.beginOpeningHandshake(ctx, channel);
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        callback.onDisconnect(this);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        if (!handshaker.isOpeningHandshakeCompleted()) {
            handshaker.endOpeningHandshake(ctx, (HttpResponse) e.getMessage());
            callback.onConnect(this);
            return;
        }

        if (e.getMessage() instanceof HttpResponse) {
            HttpResponse response = (HttpResponse) e.getMessage();
            throw new WebSocketException("Unexpected HttpResponse (status=" + response.getStatus() + ", content="
                    + response.getContent().toString(CharsetUtil.UTF_8) + ")");
        }

        WebSocketFrame frame = (WebSocketFrame) e.getMessage();
        callback.onMessage(this, frame);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        final Throwable t = e.getCause();
        callback.onError(t);
        e.getChannel().close();
    }

    @Override
    public ChannelFuture connect() {
        return bootstrap.connect(new InetSocketAddress(url.getHost(), url.getPort()));
    }

    @Override
    public ChannelFuture disconnect() {
        return channel.close();
    }

    @Override
    public ChannelFuture send(WebSocketFrame frame) {
        return channel.write(frame);
    }

    public URI getUrl() {
        return url;
    }

    public void setUrl(URI url) {
        this.url = url;
    }
}
