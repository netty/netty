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
package org.jboss.netty.handler.codec.http.websocketx;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.util.internal.StringUtil;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Base class for server side web socket opening and closing handshakes
 */
public abstract class WebSocketServerHandshaker {

    /**
     * Use this as wildcard to support all requested sub-protocols
     */
    public static final String SUB_PROTOCOL_WILDCARD = "*";

    private final String webSocketUrl;

    private final String[] subprotocols;

    private final WebSocketVersion version;

    private final long maxFramePayloadLength;

    private String selectedSubprotocol;

    /**
     * {@link ChannelFutureListener} which will call
     * {@link Channels#fireExceptionCaught(ChannelHandlerContext, Throwable)}
     * if the {@link ChannelFuture} was not successful.
     */
    public static final ChannelFutureListener HANDSHAKE_LISTENER = new ChannelFutureListener() {
        public void operationComplete(ChannelFuture future) throws Exception {
            if (!future.isSuccess()) {
                Channels.fireExceptionCaught(future.getChannel(), future.getCause());
            }
        }
    };

    /**
     * Constructor using default values
     *
     * @param version
     *            the protocol version
     * @param webSocketUrl
     *            URL for web socket communications. e.g
     *            "ws://myhost.com/mypath". Subsequent web socket frames will be
     *            sent to this URL.
     * @param subprotocols
     *            CSV of supported protocols. Null if sub protocols not
     *            supported.
     */
    protected WebSocketServerHandshaker(WebSocketVersion version, String webSocketUrl, String subprotocols) {
        this(version, webSocketUrl, subprotocols, Long.MAX_VALUE);
    }

    /**
     * Constructor specifying the destination web socket location
     *
     * @param version
     *            the protocol version
     * @param webSocketUrl
     *            URL for web socket communications. e.g
     *            "ws://myhost.com/mypath". Subsequent web socket frames will be
     *            sent to this URL.
     * @param subprotocols
     *            CSV of supported protocols. Null if sub protocols not
     *            supported.
     * @param maxFramePayloadLength
     *            Maximum length of a frame's payload
     */
    protected WebSocketServerHandshaker(WebSocketVersion version, String webSocketUrl, String subprotocols,
            long maxFramePayloadLength) {
        this.version = version;
        this.webSocketUrl = webSocketUrl;
        if (subprotocols != null) {
            String[] subprotocolArray = StringUtil.split(subprotocols, ',');
            for (int i = 0; i < subprotocolArray.length; i++) {
                subprotocolArray[i] = subprotocolArray[i].trim();
            }
            this.subprotocols = subprotocolArray;
        } else {
            this.subprotocols = new String[0];
        }
        this.maxFramePayloadLength = maxFramePayloadLength;
    }

    /**
     * Returns the URL of the web socket
     */
    public String getWebSocketUrl() {
        return webSocketUrl;
    }

    /**
     * Returns the CSV of supported sub protocols
     */
    public Set<String> getSubprotocols() {
        Set<String> ret = new LinkedHashSet<String>();
        Collections.addAll(ret, subprotocols);
        return ret;
    }

    /**
     * Returns the version of the specification being supported
     */
    public WebSocketVersion getVersion() {
        return version;
    }

    /**
     * Returns the max length for any frame's payload
     */
    public long getMaxFramePayloadLength() {
        return maxFramePayloadLength;
    }

    /**
     * Performs the opening handshake
     *
     * @param channel
     *            Channel
     * @param req
     *            HTTP Request
     */
    public abstract ChannelFuture handshake(Channel channel, HttpRequest req);

    /**
     * Upgrades the connection and send the handshake response.
     */
    protected ChannelFuture writeHandshakeResponse(
            Channel channel, HttpResponse res, ChannelHandler encoder, ChannelHandler decoder) {
        final ChannelPipeline p = channel.getPipeline();
        if (p.get(HttpChunkAggregator.class) != null) {
            p.remove(HttpChunkAggregator.class);
        }

        final String httpEncoderName = p.getContext(HttpResponseEncoder.class).getName();
        p.addAfter(httpEncoderName, "wsencoder", encoder);
        p.get(HttpRequestDecoder.class).replace("wsdecoder", decoder);

        final ChannelFuture future = channel.write(res);
        future.addListener(new ChannelFutureListener() {
            public void operationComplete(ChannelFuture future) {
                p.remove(httpEncoderName);
            }
        });

        return future;
    }

    /**
     * Performs the closing handshake
     *
     * @param channel
     *            Channel
     * @param frame
     *            Closing Frame that was received
     */
    public abstract ChannelFuture close(Channel channel, CloseWebSocketFrame frame);

    /**
     * Selects the first matching supported sub protocol
     *
     * @param requestedSubprotocols
     *            CSV of protocols to be supported. e.g. "chat, superchat"
     * @return First matching supported sub protocol. Null if not found.
     */
    protected String selectSubprotocol(String requestedSubprotocols) {
        if (requestedSubprotocols == null || subprotocols.length == 0) {
            return null;
        }

        String[] requestedSubprotocolArray = StringUtil.split(requestedSubprotocols, ',');
        for (String p : requestedSubprotocolArray) {
            String requestedSubprotocol = p.trim();

            for (String supportedSubprotocol : subprotocols) {
                if (SUB_PROTOCOL_WILDCARD.equals(supportedSubprotocol) ||
                         requestedSubprotocol.equals(supportedSubprotocol)) {
                    return requestedSubprotocol;
                }
            }
        }

        // No match found
        return null;
    }

    /**
     * Returns the selected subprotocol. Null if no subprotocol has been selected.
     * <p>
     * This is only available AFTER <tt>handshake()</tt> has been called.
     * </p>
     */
    public String getSelectedSubprotocol() {
        return selectedSubprotocol;
    }

    protected void setSelectedSubprotocol(String value) {
        selectedSubprotocol = value;
    }

}
