/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.channel.socket.http;

import static org.jboss.netty.channel.Channels.*;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.AbstractChannel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelSink;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.DefaultChannelPipeline;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.SocketChannel;
import org.jboss.netty.handler.codec.http.Cookie;
import org.jboss.netty.handler.codec.http.CookieDecoder;
import org.jboss.netty.handler.codec.http.CookieEncoder;
import org.jboss.netty.handler.codec.http.DefaultHttpChunk;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpRequestEncoder;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseDecoder;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
class HttpTunnelingClientSocketChannel extends AbstractChannel
        implements org.jboss.netty.channel.socket.SocketChannel {

    static final InternalLogger logger =
        InternalLoggerFactory.getInstance(HttpTunnelingClientSocketChannel.class);
    
    private static final String JSESSIONID = "JSESSIONID";

    private final HttpTunnelingSocketChannelConfig config;

    volatile boolean awaitingInitialResponse = true;

    private final Object writeLock = new Object();
    final Object interestOpsLock = new Object();

    volatile Thread workerThread;

    volatile String sessionId;

    volatile boolean closed = false;

    private final ClientSocketChannelFactory clientSocketChannelFactory;

    volatile SocketChannel channel;

    private final HttpTunnelingClientSocketChannel.ServletChannelHandler handler = new ServletChannelHandler();

    volatile HttpTunnelAddress remoteAddress;

    HttpTunnelingClientSocketChannel(
            ChannelFactory factory,
            ChannelPipeline pipeline,
            ChannelSink sink, ClientSocketChannelFactory clientSocketChannelFactory) {

        super(null, factory, pipeline, sink);

        this.clientSocketChannelFactory = clientSocketChannelFactory;

        createSocketChannel();
        config = new HttpTunnelingSocketChannelConfig(this);
        fireChannelOpen(this);
    }

    public HttpTunnelingSocketChannelConfig getConfig() {
        return config;
    }

    public InetSocketAddress getLocalAddress() {
        return channel.getLocalAddress();
    }

    public InetSocketAddress getRemoteAddress() {
        return channel.getRemoteAddress();
    }

    public boolean isBound() {
        return channel.isBound();
    }

    public boolean isConnected() {
        return channel.isConnected();
    }
    
    @Override
    public int getInterestOps() {
        return channel.getInterestOps();
    }
    
    @Override
    public boolean isWritable() {
        return channel.isWritable();
    }
    
    @Override
    public ChannelFuture setInterestOps(int interestOps) {
        // TODO: Wrap the future.
        return channel.setInterestOps(interestOps);
    }

    @Override
    protected boolean setClosed() {
        return super.setClosed();
    }

    @Override
    public ChannelFuture write(Object message, SocketAddress remoteAddress) {
        if (remoteAddress == null || remoteAddress.equals(getRemoteAddress())) {
            return super.write(message, null);
        }
        else {
            return getUnsupportedOperationFuture();
        }
    }

    void connectAndSendHeaders(boolean reconnect, HttpTunnelAddress remoteAddress) throws SSLException {
        this.remoteAddress = remoteAddress;
        URI url = remoteAddress.getUri();
        if (reconnect) {
            closeSocket();
            createSocketChannel();
        }
        SocketAddress connectAddress = new InetSocketAddress(url.getHost(), url.getPort());
        channel.connect(connectAddress).awaitUninterruptibly();

        // Configure SSL
        HttpTunnelingSocketChannelConfig config = getConfig();
        SSLContext sslContext = config.getSslContext();
        if (sslContext != null) {
            URI uri = remoteAddress.getUri();
            SSLEngine engine = sslContext.createSSLEngine(
                    uri.getHost(), uri.getPort());

            // Configure the SSLEngine.
            engine.setUseClientMode(true);
            engine.setEnableSessionCreation(config.isEnableSslSessionCreation());
            String[] enabledCipherSuites = config.getEnabledSslCipherSuites();
            if (enabledCipherSuites != null) {
                engine.setEnabledCipherSuites(enabledCipherSuites);
            }
            String[] enabledProtocols = config.getEnabledSslProtocols();
            if (enabledProtocols != null) {
                engine.setEnabledProtocols(enabledProtocols);
            }

            SslHandler sslHandler = new SslHandler(engine);
            channel.getPipeline().addFirst("ssl", sslHandler);
            sslHandler.handshake(channel).awaitUninterruptibly();
        }

        // Send the HTTP request.
        HttpRequest req = new DefaultHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.POST, url.getRawPath());
        req.setHeader(HttpHeaders.Names.HOST, url.getHost());
        req.setHeader(HttpHeaders.Names.CONTENT_TYPE, "application/octet-stream");
        req.setHeader(HttpHeaders.Names.TRANSFER_ENCODING, HttpHeaders.Values.CHUNKED);
        req.setHeader(HttpHeaders.Names.CONTENT_TRANSFER_ENCODING, HttpHeaders.Values.BINARY);
        
        if (sessionId != null) {
            CookieEncoder ce = new CookieEncoder(false);
            ce.addCookie(JSESSIONID, sessionId);
            req.setHeader(HttpHeaders.Names.COOKIE, ce.encode());
        }

        channel.write(req);
    }

    private void createSocketChannel() {
        DefaultChannelPipeline channelPipeline = new DefaultChannelPipeline();
        // TODO Expose the codec options via HttpTunnelingSocketChannelConfig
        channelPipeline.addLast("decoder", new HttpResponseDecoder());
        channelPipeline.addLast("encoder", new HttpRequestEncoder());
        channelPipeline.addLast("handler", handler);
        channel = clientSocketChannelFactory.newChannel(channelPipeline);
    }

    int sendChunk(ChannelBuffer a) {
        int size = a.readableBytes();
        String hex = Integer.toHexString(size) + HttpTunnelingClientSocketPipelineSink.LINE_TERMINATOR;

        synchronized (writeLock) {
            channel.write(new DefaultHttpChunk(a)).awaitUninterruptibly();
        }

        return size + hex.length() + HttpTunnelingClientSocketPipelineSink.LINE_TERMINATOR.length();
    }

    void closeSocket() {
        if (setClosed()) {
            // Send the end of chunk.
            synchronized (writeLock) {
                channel.write(HttpChunk.LAST_CHUNK).awaitUninterruptibly();
            }

            closed = true;
            channel.close();
        }
    }

    void bindSocket(SocketAddress localAddress) {
        channel.bind(localAddress);
    }

    @ChannelPipelineCoverage("one")
    class ServletChannelHandler extends SimpleChannelUpstreamHandler {
        
        private volatile boolean readingChunks;
        int nextChunkSize = -1;

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
            if (!readingChunks) {
                HttpResponse res = (HttpResponse) e.getMessage();
                String newSessionId = null;
                newSessionId = getSessionId(res, HttpHeaders.Names.SET_COOKIE);
                if (newSessionId == null) {
                    newSessionId = getSessionId(res, HttpHeaders.Names.SET_COOKIE2);
                }
                
                // XXX: Utilize keep-alive if possible to reduce reconnection overhead.
                // XXX: Consider non-200 status code.
                //      If the status code is not 200, no more reconnection attempt
                //      should be made.
                // XXX: If the session ID in the response is different from
                //      the session ID specified in the request, then it means
                //      the session has timed out.  If so, channel must be closed.
                
                sessionId = newSessionId;
                
                if (res.isChunked()) {
                    readingChunks = true;
                } else {
                    ChannelBuffer content = res.getContent();
                    if (content.readable()) {
                        fireMessageReceived(channel, content);
                    }
                }
            } else {
                HttpChunk chunk = (HttpChunk) e.getMessage();
                if (!chunk.isLast()) {
                    fireMessageReceived(channel, chunk.getContent());
                } else {
                    readingChunks = false;
                }
                
            }
        }

        @Override
        public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
                throws Exception {
            if (sessionId != null) {
                // TODO Reconnect.
            } else {
                // sessionId is null if:
                // 1) A user closed the channel explicitly, or
                // 2) The server does not support JSESSIONID.
                channel.close();
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
            fireExceptionCaught(
                    HttpTunnelingClientSocketChannel.this,
                    e.getCause());
            channel.close();
        }
    }
    
    static String getSessionId(HttpResponse res, String headerName) {
        CookieDecoder decoder = null;
        for (String v: res.getHeaders(headerName)) {
            if (decoder == null) {
                decoder = new CookieDecoder();
            }
            
            for (Cookie c: decoder.decode(v)) {
                if (c.getName().equals(JSESSIONID)) {
                    return c.getValue();
                }
            }
        }
        return null;
    }
}