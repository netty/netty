/*
 * Copyright 2009 Red Hat, Inc.
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
package org.jboss.netty.channel.socket.http;

import static org.jboss.netty.channel.Channels.*;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.NotYetConnectedException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.AbstractChannel;
import org.jboss.netty.channel.ChannelException;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelSink;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.DefaultChannelPipeline;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.SocketChannel;
import org.jboss.netty.handler.codec.http.DefaultHttpChunk;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpRequestEncoder;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.ssl.SslHandler;

/**
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev: 2285 $, $Date: 2010-05-27 21:02:49 +0900 (Thu, 27 May 2010) $
 */
class HttpTunnelingClientSocketChannel extends AbstractChannel
        implements org.jboss.netty.channel.socket.SocketChannel {

    final HttpTunnelingSocketChannelConfig config;

    volatile boolean requestHeaderWritten;

    final Object interestOpsLock = new Object();

    final SocketChannel realChannel;

    private final HttpTunnelingClientSocketChannel.ServletChannelHandler handler = new ServletChannelHandler();

    HttpTunnelingClientSocketChannel(
            ChannelFactory factory,
            ChannelPipeline pipeline,
            ChannelSink sink, ClientSocketChannelFactory clientSocketChannelFactory) {

        super(null, factory, pipeline, sink);

        config = new HttpTunnelingSocketChannelConfig(this);
        DefaultChannelPipeline channelPipeline = new DefaultChannelPipeline();
        channelPipeline.addLast("decoder", new HttpResponseDecoder());
        channelPipeline.addLast("encoder", new HttpRequestEncoder());
        channelPipeline.addLast("handler", handler);
        realChannel = clientSocketChannelFactory.newChannel(channelPipeline);

        fireChannelOpen(this);
    }

    public HttpTunnelingSocketChannelConfig getConfig() {
        return config;
    }

    public InetSocketAddress getLocalAddress() {
        return realChannel.getLocalAddress();
    }

    public InetSocketAddress getRemoteAddress() {
        return realChannel.getRemoteAddress();
    }

    public boolean isBound() {
        return realChannel.isBound();
    }

    public boolean isConnected() {
        return realChannel.isConnected();
    }

    @Override
    public int getInterestOps() {
        return realChannel.getInterestOps();
    }

    @Override
    public boolean isWritable() {
        return realChannel.isWritable();
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

    void bindReal(final SocketAddress localAddress, final ChannelFuture future) {
        realChannel.bind(localAddress).addListener(new ChannelFutureListener() {
            public void operationComplete(ChannelFuture f) {
                if (f.isSuccess()) {
                    future.setSuccess();
                } else {
                    future.setFailure(f.getCause());
                }
            }
        });
    }

    void connectReal(final SocketAddress remoteAddress, final ChannelFuture future) {
        final SocketChannel virtualChannel = this;
        realChannel.connect(remoteAddress).addListener(new ChannelFutureListener() {
            public void operationComplete(ChannelFuture f) {
                final String serverName = config.getServerName();
                final int serverPort = ((InetSocketAddress) remoteAddress).getPort();
                final String serverPath = config.getServerPath();

                if (f.isSuccess()) {
                    // Configure SSL
                    SSLContext sslContext = config.getSslContext();
                    ChannelFuture sslHandshakeFuture = null;
                    if (sslContext != null) {
                        // Create a new SSLEngine from the specified SSLContext.
                        SSLEngine engine;
                        if (serverName != null) {
                            engine = sslContext.createSSLEngine(serverName, serverPort);
                        } else {
                            engine = sslContext.createSSLEngine();
                        }

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
                        realChannel.getPipeline().addFirst("ssl", sslHandler);
                        sslHandshakeFuture = sslHandler.handshake();
                    }

                    // Send the HTTP request.
                    final HttpRequest req = new DefaultHttpRequest(
                            HttpVersion.HTTP_1_1, HttpMethod.POST, serverPath);
                    if (serverName != null) {
                        req.setHeader(HttpHeaders.Names.HOST, serverName);
                    }
                    req.setHeader(HttpHeaders.Names.CONTENT_TYPE, "application/octet-stream");
                    req.setHeader(HttpHeaders.Names.TRANSFER_ENCODING, HttpHeaders.Values.CHUNKED);
                    req.setHeader(HttpHeaders.Names.CONTENT_TRANSFER_ENCODING, HttpHeaders.Values.BINARY);
                    req.setHeader(HttpHeaders.Names.USER_AGENT, HttpTunnelingClientSocketChannel.class.getName());

                    if (sslHandshakeFuture == null) {
                        realChannel.write(req);
                        requestHeaderWritten = true;
                        future.setSuccess();
                        fireChannelConnected(virtualChannel, remoteAddress);
                    } else {
                        sslHandshakeFuture.addListener(new ChannelFutureListener() {
                            public void operationComplete(ChannelFuture f) {
                                if (f.isSuccess()) {
                                    realChannel.write(req);
                                    requestHeaderWritten = true;
                                    future.setSuccess();
                                    fireChannelConnected(virtualChannel, remoteAddress);
                                } else {
                                    future.setFailure(f.getCause());
                                    fireExceptionCaught(virtualChannel, f.getCause());
                                }
                            }
                        });
                    }
                } else {
                    future.setFailure(f.getCause());
                    fireExceptionCaught(virtualChannel, f.getCause());
                }
            }
        });
    }

    void writeReal(final ChannelBuffer a, final ChannelFuture future) {
        if (!requestHeaderWritten) {
            throw new NotYetConnectedException();
        }

        final int size = a.readableBytes();
        final ChannelFuture f;

        if (size == 0) {
            f = realChannel.write(ChannelBuffers.EMPTY_BUFFER);
        } else {
            f = realChannel.write(new DefaultHttpChunk(a));
        }

        f.addListener(new ChannelFutureListener() {
            public void operationComplete(ChannelFuture f) {
                if (f.isSuccess()) {
                    future.setSuccess();
                    if (size != 0) {
                        fireWriteComplete(HttpTunnelingClientSocketChannel.this, size);
                    }
                } else {
                    future.setFailure(f.getCause());
                }
            }
        });
    }

    private ChannelFuture writeLastChunk() {
        if (!requestHeaderWritten) {
            throw new NotYetConnectedException();
        } else {
            return realChannel.write(HttpChunk.LAST_CHUNK);
        }
    }

    void setInterestOpsReal(final int interestOps, final ChannelFuture future) {
        realChannel.setInterestOps(interestOps).addListener(new ChannelFutureListener() {
            public void operationComplete(ChannelFuture f) {
                if (f.isSuccess()) {
                    future.setSuccess();
                } else {
                    future.setFailure(f.getCause());
                }
            }
        });
    }

    void disconnectReal(final ChannelFuture future) {
        writeLastChunk().addListener(new ChannelFutureListener() {
            public void operationComplete(ChannelFuture f) {
                realChannel.disconnect().addListener(new ChannelFutureListener() {
                    public void operationComplete(ChannelFuture f) {
                        if (f.isSuccess()) {
                            future.setSuccess();
                        } else {
                            future.setFailure(f.getCause());
                        }
                    }
                });
            }
        });
    }

    void unbindReal(final ChannelFuture future) {
        writeLastChunk().addListener(new ChannelFutureListener() {
            public void operationComplete(ChannelFuture f) {
                realChannel.unbind().addListener(new ChannelFutureListener() {
                    public void operationComplete(ChannelFuture f) {
                        if (f.isSuccess()) {
                            future.setSuccess();
                        } else {
                            future.setFailure(f.getCause());
                        }
                    }
                });
            }
        });
    }

    void closeReal(final ChannelFuture future) {
        writeLastChunk().addListener(new ChannelFutureListener() {
            public void operationComplete(ChannelFuture f) {
                realChannel.close().addListener(new ChannelFutureListener() {
                    public void operationComplete(ChannelFuture f) {
                        // Note: If 'future' refers to the closeFuture,
                        // setSuccess() and setFailure() do nothing.
                        // AbstractChannel.setClosed() should be called instead.
                        // (See AbstractChannel.ChannelCloseFuture)

                        if (f.isSuccess()) {
                            future.setSuccess();
                        } else {
                            future.setFailure(f.getCause());
                        }

                        // Notify the closeFuture.
                        setClosed();
                    }
                });
            }
        });
    }

    final class ServletChannelHandler extends SimpleChannelUpstreamHandler {

        private volatile boolean readingChunks;
        final SocketChannel virtualChannel = HttpTunnelingClientSocketChannel.this;

        @Override
        public void channelBound(ChannelHandlerContext ctx, ChannelStateEvent e)
                throws Exception {
            fireChannelBound(virtualChannel, (SocketAddress) e.getValue());
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
            if (!readingChunks) {
                HttpResponse res = (HttpResponse) e.getMessage();
                if (res.getStatus().getCode() != HttpResponseStatus.OK.getCode()) {
                    throw new ChannelException("Unexpected HTTP response status: " + res.getStatus());
                }

                if (res.isChunked()) {
                    readingChunks = true;
                } else {
                    ChannelBuffer content = res.getContent();
                    if (content.readable()) {
                        fireMessageReceived(HttpTunnelingClientSocketChannel.this, content);
                    }
                    // Reached to the end of response - close the request.
                    closeReal(succeededFuture(virtualChannel));
                }
            } else {
                HttpChunk chunk = (HttpChunk) e.getMessage();
                if (!chunk.isLast()) {
                    fireMessageReceived(HttpTunnelingClientSocketChannel.this, chunk.getContent());
                } else {
                    readingChunks = false;
                    // Reached to the end of response - close the request.
                    closeReal(succeededFuture(virtualChannel));
                }
            }
        }

        @Override
        public void channelInterestChanged(ChannelHandlerContext ctx,
                ChannelStateEvent e) throws Exception {
            fireChannelInterestChanged(virtualChannel);
        }

        @Override
        public void channelDisconnected(ChannelHandlerContext ctx,
                ChannelStateEvent e) throws Exception {
            fireChannelDisconnected(virtualChannel);
        }

        @Override
        public void channelUnbound(ChannelHandlerContext ctx,
                ChannelStateEvent e) throws Exception {
            fireChannelUnbound(virtualChannel);
        }

        @Override
        public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
                throws Exception {
            fireChannelClosed(virtualChannel);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
            fireExceptionCaught(virtualChannel, e.getCause());
            realChannel.close();
        }
    }
}
