/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.http;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.ReferenceCounted;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.*;
import static java.lang.String.*;

/**
 * A server-side handler that receives HTTP requests and optionally performs a protocol switch if
 * the requested protocol is supported. Once an upgrade is performed, this handler removes itself
 * from the pipeline.
 */
public class HttpServerUpgradeHandler extends HttpObjectAggregator {

    /**
     * The source codec that is used in the pipeline initially.
     */
    public interface SourceCodec {
        /**
         * Removes this codec (i.e. all associated handlers) from the pipeline.
         */
        void upgradeFrom(ChannelHandlerContext ctx);
    }

    /**
     * A codec that the source can be upgraded to.
     */
    public interface UpgradeCodec {
        /**
         * Returns the name of the protocol supported by this codec, as indicated by the
         * {@link HttpHeaders.Names#UPGRADE} header.
         */
        String protocol();

        /**
         * Gets all protocol-specific headers required by this protocol for a successful upgrade.
         * Any supplied header will be required to appear in the {@link HttpHeaders.Names#CONNECTION} header as well.
         */
        Collection<String> requiredUpgradeHeaders();

        /**
         * Adds any headers to the 101 Switching protocols response that are appropriate for this protocol.
         */
        void prepareUpgradeResponse(ChannelHandlerContext ctx, FullHttpRequest upgradeRequest,
                FullHttpResponse upgradeResponse);

        /**
         * Performs an HTTP protocol upgrade from the source codec. This method is responsible for
         * adding all handlers required for the new protocol.
         *
         * @param ctx the context for the current handler.
         * @param upgradeRequest the request that triggered the upgrade to this protocol. The
         *            upgraded protocol is responsible for sending the response.
         * @param upgradeResponse a 101 Switching Protocols response that is populated with the
         *            {@link HttpHeaders.Names#CONNECTION} and {@link HttpHeaders.Names#UPGRADE} headers.
         *            The protocol is required to send this before sending any other frames back to the client.
         *            The headers may be augmented as necessary by the protocol before sending.
         */
        void upgradeTo(ChannelHandlerContext ctx, FullHttpRequest upgradeRequest, FullHttpResponse upgradeResponse);
    }

    /**
     * User event that is fired to notify about the completion of an HTTP upgrade
     * to another protocol. Contains the original upgrade request so that the response
     * (if required) can be sent using the new protocol.
     */
    public static final class UpgradeEvent implements ReferenceCounted {
        private final String protocol;
        private final FullHttpRequest upgradeRequest;

        private UpgradeEvent(String protocol, FullHttpRequest upgradeRequest) {
            this.protocol = protocol;
            this.upgradeRequest = upgradeRequest;
        }

        /**
         * The protocol that the channel has been upgraded to.
         */
        public String protocol() {
            return protocol;
        }

        /**
         * Gets the request that triggered the protocol upgrade.
         */
        public FullHttpRequest upgradeRequest() {
            return upgradeRequest;
        }

        @Override
        public int refCnt() {
            return upgradeRequest.refCnt();
        }

        @Override
        public UpgradeEvent retain() {
            upgradeRequest.retain();
            return this;
        }

        @Override
        public UpgradeEvent retain(int increment) {
            upgradeRequest.retain(increment);
            return this;
        }

        @Override
        public UpgradeEvent touch() {
            upgradeRequest.touch();
            return this;
        }

        @Override
        public UpgradeEvent touch(Object hint) {
            upgradeRequest.touch(hint);
            return this;
        }

        @Override
        public boolean release() {
            return upgradeRequest.release();
        }

        @Override
        public boolean release(int decrement) {
            return upgradeRequest.release();
        }

        @Override
        public String toString() {
            return "UpgradeEvent [protocol=" + protocol + ", upgradeRequest=" + upgradeRequest + ']';
        }
    }

    private final Map<String, UpgradeCodec> upgradeCodecMap;
    private final SourceCodec sourceCodec;
    private boolean handlingUpgrade;

    /**
     * Constructs the upgrader with the supported codecs.
     *
     * @param sourceCodec the codec that is being used initially.
     * @param upgradeCodecs the codecs (in order of preference) that this server supports
     *            upgrading to from the source codec.
     * @param maxContentLength the maximum length of the aggregated content.
     */
    public HttpServerUpgradeHandler(SourceCodec sourceCodec,
            Collection<UpgradeCodec> upgradeCodecs, int maxContentLength) {
        super(maxContentLength);
        if (sourceCodec == null) {
            throw new NullPointerException("sourceCodec");
        }
        if (upgradeCodecs == null) {
            throw new NullPointerException("upgradeCodecs");
        }
        this.sourceCodec = sourceCodec;
        upgradeCodecMap = new LinkedHashMap<String, UpgradeCodec>(upgradeCodecs.size());
        for (UpgradeCodec upgradeCodec : upgradeCodecs) {
            String name = upgradeCodec.protocol().toUpperCase(Locale.US);
            upgradeCodecMap.put(name, upgradeCodec);
        }
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, HttpObject msg, List<Object> out)
            throws Exception {
        // Determine if we're already handling an upgrade request or just starting a new one.
        handlingUpgrade |= isUpgradeRequest(msg);
        if (!handlingUpgrade) {
            // Not handling an upgrade request, just pass it to the next handler.
            ReferenceCountUtil.retain(msg);
            out.add(msg);
            return;
        }

        FullHttpRequest fullRequest;
        if (msg instanceof FullHttpRequest) {
            fullRequest = (FullHttpRequest) msg;
            ReferenceCountUtil.retain(msg);
            out.add(msg);
        } else {
            // Call the base class to handle the aggregation of the full request.
            super.decode(ctx, msg, out);
            if (out.isEmpty()) {
                // The full request hasn't been created yet, still awaiting more data.
                return;
            }

            // Finished aggregating the full request, get it from the output list.
            assert out.size() == 1;
            handlingUpgrade = false;
            fullRequest = (FullHttpRequest) out.get(0);
        }

        if (upgrade(ctx, fullRequest)) {
            // The upgrade was successful, remove the message from the output list
            // so that it's not propagated to the next handler. This request will
            // be propagated as a user event instead.
            out.clear();
        }

        // The upgrade did not succeed, just allow the full request to propagate to the
        // next handler.
    }

    /**
     * Determines whether or not the message is an HTTP upgrade request.
     */
    private static boolean isUpgradeRequest(HttpObject msg) {
        return msg instanceof HttpRequest && ((HttpRequest) msg).headers().get(UPGRADE) != null;
    }

    /**
     * Attempts to upgrade to the protocol(s) identified by the {@link HttpHeaders.Names#UPGRADE} header (if provided
     * in the request).
     *
     * @param ctx the context for this handler.
     * @param request the HTTP request.
     * @return {@code true} if the upgrade occurred, otherwise {@code false}.
     */
    private boolean upgrade(final ChannelHandlerContext ctx, final FullHttpRequest request) {
        // Select the best protocol based on those requested in the UPGRADE header.
        String upgradeHeader = request.headers().get(UPGRADE);
        final UpgradeCodec upgradeCodec = selectUpgradeCodec(upgradeHeader);
        if (upgradeCodec == null) {
            // None of the requested protocols are supported, don't upgrade.
            return false;
        }

        // Make sure the CONNECTION header is present.
        String connectionHeader = request.headers().get(CONNECTION);
        if (connectionHeader == null) {
            return false;
        }

        // Make sure the CONNECTION header contains UPGRADE as well as all protocol-specific headers.
        Collection<String> requiredHeaders = upgradeCodec.requiredUpgradeHeaders();
        Set<String> values = splitHeader(connectionHeader);
        if (!values.contains(UPGRADE.toString()) || !values.containsAll(requiredHeaders)) {
            return false;
        }

        // Ensure that all required protocol-specific headers are found in the request.
        for (String requiredHeader : requiredHeaders) {
            if (!request.headers().contains(requiredHeader)) {
                return false;
            }
        }

        // Create the user event to be fired once the upgrade completes.
        final UpgradeEvent event = new UpgradeEvent(upgradeCodec.protocol(), request);

        // Prepare and send the upgrade response. Wait for this write to complete before upgrading,
        // since we need the old codec in-place to properly encode the response.
        final FullHttpResponse upgradeResponse = createUpgradeResponse(upgradeCodec);
        upgradeCodec.prepareUpgradeResponse(ctx, request, upgradeResponse);
        ctx.writeAndFlush(upgradeResponse).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                try {
                    if (future.isSuccess()) {
                        // Perform the upgrade to the new protocol.
                        sourceCodec.upgradeFrom(ctx);
                        upgradeCodec.upgradeTo(ctx, request, upgradeResponse);

                        // Notify that the upgrade has occurred. Retain the event to offset
                        // the release() in the finally block.
                        ctx.fireUserEventTriggered(event.retain());

                        // Remove this handler from the pipeline.
                        ctx.pipeline().remove(HttpServerUpgradeHandler.this);
                    } else {
                        future.channel().close();
                    }
                } finally {
                    // Release the event if the upgrade event wasn't fired.
                    event.release();
                }
            }
        });
        return true;
    }

    /**
     * Looks up the most desirable supported upgrade codec from the list of choices in the UPGRADE
     * header. If no suitable codec was found, returns {@code null}.
     */
    private UpgradeCodec selectUpgradeCodec(String upgradeHeader) {
        Set<String> requestedProtocols = splitHeader(upgradeHeader);

        // Retain only the protocols that are in the protocol map. Maintain the original insertion
        // order into the protocolMap, so that the first one in the remaining set is the most
        // desirable protocol for the server.
        Set<String> supportedProtocols = new LinkedHashSet<String>(upgradeCodecMap.keySet());
        supportedProtocols.retainAll(requestedProtocols);

        if (!supportedProtocols.isEmpty()) {
            String protocol = supportedProtocols.iterator().next().toUpperCase(Locale.US);
            return upgradeCodecMap.get(protocol);
        }
        return null;
    }

    /**
     * Creates the 101 Switching Protocols response message.
     */
    private static FullHttpResponse createUpgradeResponse(UpgradeCodec upgradeCodec) {
        DefaultFullHttpResponse res = new DefaultFullHttpResponse(HTTP_1_1, SWITCHING_PROTOCOLS);
        res.headers().add(CONNECTION, UPGRADE);
        res.headers().add(UPGRADE, upgradeCodec.protocol());
        res.headers().add(CONTENT_LENGTH, "0");
        return res;
    }

    /**
     * Splits a comma-separated header value. The returned set is case-insensitive and contains each
     * part with whitespace removed.
     */
    private static Set<String> splitHeader(String header) {
        StringBuilder builder = new StringBuilder(header.length());
        Set<String> protocols = new TreeSet<String>(CASE_INSENSITIVE_ORDER);
        for (int i = 0; i < header.length(); ++i) {
            char c = header.charAt(i);
            if (Character.isWhitespace(c)) {
                // Don't include any whitespace.
                continue;
            }
            if (c == ',') {
                // Add the string and reset the builder for the next protocol.
                protocols.add(builder.toString());
                builder.setLength(0);
            } else {
                builder.append(c);
            }
        }

        // Add the last protocol
        if (builder.length() > 0) {
            protocols.add(builder.toString());
        }

        return protocols;
    }
}
