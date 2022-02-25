/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty5.handler.codec.http;

import io.netty5.buffer.api.BufferAllocator;
import io.netty5.buffer.api.Resource;
import io.netty5.buffer.api.Send;
import io.netty5.channel.ChannelFutureListeners;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.util.ReferenceCountUtil;
import io.netty5.util.concurrent.Future;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static io.netty5.handler.codec.http.HttpResponseStatus.SWITCHING_PROTOCOLS;
import static io.netty5.handler.codec.http.HttpVersion.HTTP_1_1;
import static io.netty5.util.AsciiString.containsAllContentEqualsIgnoreCase;
import static io.netty5.util.AsciiString.containsContentEqualsIgnoreCase;
import static io.netty5.util.internal.StringUtil.COMMA;
import static java.util.Objects.requireNonNull;

/**
 * A server-side handler that receives HTTP requests and optionally performs a protocol switch if
 * the requested protocol is supported. Once an upgrade is performed, this handler removes itself
 * from the pipeline.
 */
public class HttpServerUpgradeHandler<C extends HttpContent<C>> extends HttpObjectAggregator<C> {

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
         * Gets all protocol-specific headers required by this protocol for a successful upgrade.
         * Any supplied header will be required to appear in the {@link HttpHeaderNames#CONNECTION} header as well.
         */
        Collection<CharSequence> requiredUpgradeHeaders();

        /**
         * Prepares the {@code upgradeHeaders} for a protocol update based upon the contents of {@code upgradeRequest}.
         * This method returns a boolean value to proceed or abort the upgrade in progress. If {@code false} is
         * returned, the upgrade is aborted and the {@code upgradeRequest} will be passed through the inbound pipeline
         * as if no upgrade was performed. If {@code true} is returned, the upgrade will proceed to the next
         * step which invokes {@link #upgradeTo}. When returning {@code true}, you can add headers to
         * the {@code upgradeHeaders} so that they are added to the 101 Switching protocols response.
         */
        boolean prepareUpgradeResponse(ChannelHandlerContext ctx, FullHttpRequest upgradeRequest,
                                    HttpHeaders upgradeHeaders);

        /**
         * Performs an HTTP protocol upgrade from the source codec. This method is responsible for
         * adding all handlers required for the new protocol.
         *
         * @param ctx the context for the current handler.
         * @param upgradeRequest the request that triggered the upgrade to this protocol.
         */
        void upgradeTo(ChannelHandlerContext ctx, FullHttpRequest upgradeRequest);
    }

    /**
     * Creates a new {@link UpgradeCodec} for the requested protocol name.
     */
    public interface UpgradeCodecFactory {
        /**
         * Invoked by {@link HttpServerUpgradeHandler} for all the requested protocol names in the order of
         * the client preference. The first non-{@code null} {@link UpgradeCodec} returned by this method
         * will be selected.
         *
         * @return a new {@link UpgradeCodec}, or {@code null} if the specified protocol name is not supported
         */
        UpgradeCodec newUpgradeCodec(CharSequence protocol);
    }

    /**
     * User event that is fired to notify about the completion of an HTTP upgrade
     * to another protocol. Contains the original upgrade request so that the response
     * (if required) can be sent using the new protocol.
     */
    public static final class UpgradeEvent implements Resource<UpgradeEvent> {
        private final CharSequence protocol;
        private final FullHttpRequest upgradeRequest;

        UpgradeEvent(CharSequence protocol, FullHttpRequest upgradeRequest) {
            this.protocol = protocol;
            this.upgradeRequest = upgradeRequest;
        }

        /**
         * The protocol that the channel has been upgraded to.
         */
        public CharSequence protocol() {
            return protocol;
        }

        /**
         * Gets the request that triggered the protocol upgrade.
         */
        public FullHttpRequest upgradeRequest() {
            return upgradeRequest;
        }

        @Override
        public Send<UpgradeEvent> send() {
            return upgradeRequest.send().map(UpgradeEvent.class, req -> new UpgradeEvent(protocol, req));
        }

        @Override
        public void close() {
            upgradeRequest.close();
        }

        @Override
        public boolean isAccessible() {
            return upgradeRequest.isAccessible();
        }

        @Override
        public UpgradeEvent touch(Object hint) {
            upgradeRequest.touch(hint);
            return this;
        }

        @Override
        public String toString() {
            return "UpgradeEvent [protocol=" + protocol + ", upgradeRequest=" + upgradeRequest + ']';
        }
    }

    private final SourceCodec sourceCodec;
    private final UpgradeCodecFactory upgradeCodecFactory;
    private final boolean validateHeaders;
    private boolean handlingUpgrade;

    /**
     * Constructs the upgrader with the supported codecs.
     * <p>
     * The handler instantiated by this constructor will reject an upgrade request with non-empty content.
     * It should not be a concern because an upgrade request is most likely a GET request.
     * If you have a client that sends a non-GET upgrade request, please consider using
     * {@link #HttpServerUpgradeHandler(SourceCodec, UpgradeCodecFactory, int)} to specify the maximum
     * length of the content of an upgrade request.
     * </p>
     *
     * @param sourceCodec the codec that is being used initially
     * @param upgradeCodecFactory the factory that creates a new upgrade codec
     *                            for one of the requested upgrade protocols
     */
    public HttpServerUpgradeHandler(SourceCodec sourceCodec, UpgradeCodecFactory upgradeCodecFactory) {
        this(sourceCodec, upgradeCodecFactory, 0);
    }

    /**
     * Constructs the upgrader with the supported codecs.
     *
     * @param sourceCodec the codec that is being used initially
     * @param upgradeCodecFactory the factory that creates a new upgrade codec
     *                            for one of the requested upgrade protocols
     * @param maxContentLength the maximum length of the content of an upgrade request
     */
    public HttpServerUpgradeHandler(
            SourceCodec sourceCodec, UpgradeCodecFactory upgradeCodecFactory, int maxContentLength) {
        this(sourceCodec, upgradeCodecFactory, maxContentLength, true);
    }

    /**
     * Constructs the upgrader with the supported codecs.
     *
     * @param sourceCodec the codec that is being used initially
     * @param upgradeCodecFactory the factory that creates a new upgrade codec
     *                            for one of the requested upgrade protocols
     * @param maxContentLength the maximum length of the content of an upgrade request
     * @param validateHeaders validate the header names and values of the upgrade response.
     */
    public HttpServerUpgradeHandler(SourceCodec sourceCodec, UpgradeCodecFactory upgradeCodecFactory,
                                    int maxContentLength, boolean validateHeaders) {
        super(maxContentLength);

        this.sourceCodec = requireNonNull(sourceCodec, "sourceCodec");
        this.upgradeCodecFactory = requireNonNull(upgradeCodecFactory, "upgradeCodecFactory");
        this.validateHeaders = validateHeaders;
    }

    @Override
    protected void decode(final ChannelHandlerContext ctx, HttpObject msg)
            throws Exception {

        if (!handlingUpgrade) {
            // Not handling an upgrade request yet. Check if we received a new upgrade request.
            if (msg instanceof HttpRequest) {
                HttpRequest req = (HttpRequest) msg;
                if (req.headers().contains(HttpHeaderNames.UPGRADE) &&
                    shouldHandleUpgradeRequest(req)) {
                    handlingUpgrade = true;
                } else {
                    ReferenceCountUtil.retain(msg);
                    ctx.fireChannelRead(msg);
                    return;
                }
            } else {
                ReferenceCountUtil.retain(msg);
                ctx.fireChannelRead(msg);
                return;
            }
        }

        FullHttpRequest fullRequest;
        if (msg instanceof FullHttpRequest) {
            fullRequest = (FullHttpRequest) msg;
            tryUpgrade(ctx, fullRequest);
        } else {
            // Call the base class to handle the aggregation of the full request.
            super.decode(new DelegatingChannelHandlerContext(ctx) {
                @Override
                public ChannelHandlerContext fireChannelRead(Object msg) {
                    // Finished aggregating the full request, get it from the output list.
                    handlingUpgrade = false;
                    tryUpgrade(ctx, (FullHttpRequest) msg);
                    return this;
                }
            }, msg);
        }
    }

    private void tryUpgrade(ChannelHandlerContext ctx, FullHttpRequest request) {
        if (!upgrade(ctx, request)) {

            // The upgrade did not succeed, just allow the full request to propagate to the
            // next handler.
            ctx.fireChannelRead(request);
        }
    }

    /**
     * Determines whether the specified upgrade {@link HttpRequest} should be handled by this handler or not.
     * This method will be invoked only when the request contains an {@code Upgrade} header.
     * It always returns {@code true} by default, which means any request with an {@code Upgrade} header
     * will be handled. You can override this method to ignore certain {@code Upgrade} headers, for example:
     * <pre>{@code
     * @Override
     * protected boolean isUpgradeRequest(HttpRequest req) {
     *   // Do not handle WebSocket upgrades.
     *   return !req.headers().contains(HttpHeaderNames.UPGRADE, "websocket", false);
     * }
     * }</pre>
     */
    protected boolean shouldHandleUpgradeRequest(HttpRequest req) {
        return true;
    }

    /**
     * Attempts to upgrade to the protocol(s) identified by the {@link HttpHeaderNames#UPGRADE} header (if provided
     * in the request).
     *
     * @param ctx the context for this handler.
     * @param request the HTTP request.
     * @return {@code true} if the upgrade occurred, otherwise {@code false}.
     */
    private boolean upgrade(final ChannelHandlerContext ctx, final FullHttpRequest request) {
        // Select the best protocol based on those requested in the UPGRADE header.
        final List<CharSequence> requestedProtocols = splitHeader(request.headers().get(HttpHeaderNames.UPGRADE));
        final int numRequestedProtocols = requestedProtocols.size();
        UpgradeCodec upgradeCodec = null;
        CharSequence upgradeProtocol = null;
        for (int i = 0; i < numRequestedProtocols; i ++) {
            final CharSequence p = requestedProtocols.get(i);
            final UpgradeCodec c = upgradeCodecFactory.newUpgradeCodec(p);
            if (c != null) {
                upgradeProtocol = p;
                upgradeCodec = c;
                break;
            }
        }

        if (upgradeCodec == null) {
            // None of the requested protocols are supported, don't upgrade.
            return false;
        }

        // Make sure the CONNECTION header is present.
        List<String> connectionHeaderValues = request.headers().getAll(HttpHeaderNames.CONNECTION);

        if (connectionHeaderValues == null || connectionHeaderValues.isEmpty()) {
            return false;
        }

        final StringBuilder concatenatedConnectionValue = new StringBuilder(connectionHeaderValues.size() * 10);
        for (CharSequence connectionHeaderValue : connectionHeaderValues) {
            concatenatedConnectionValue.append(connectionHeaderValue).append(COMMA);
        }
        concatenatedConnectionValue.setLength(concatenatedConnectionValue.length() - 1);

        // Make sure the CONNECTION header contains UPGRADE as well as all protocol-specific headers.
        Collection<CharSequence> requiredHeaders = upgradeCodec.requiredUpgradeHeaders();
        List<CharSequence> values = splitHeader(concatenatedConnectionValue);
        if (!containsContentEqualsIgnoreCase(values, HttpHeaderNames.UPGRADE) ||
                !containsAllContentEqualsIgnoreCase(values, requiredHeaders)) {
            return false;
        }

        // Ensure that all required protocol-specific headers are found in the request.
        for (CharSequence requiredHeader : requiredHeaders) {
            if (!request.headers().contains(requiredHeader)) {
                return false;
            }
        }

        // Prepare and send the upgrade response. Wait for this write to complete before upgrading,
        // since we need the old codec in-place to properly encode the response.
        final FullHttpResponse upgradeResponse = createUpgradeResponse(ctx.bufferAllocator(), upgradeProtocol);
        if (!upgradeCodec.prepareUpgradeResponse(ctx, request, upgradeResponse.headers())) {
            return false;
        }

        // After writing the upgrade response we immediately prepare the
        // pipeline for the next protocol to avoid a race between completion
        // of the write future and receiving data before the pipeline is
        // restructured.
        Future<Void> writeComplete = ctx.writeAndFlush(upgradeResponse);
        // Perform the upgrade to the new protocol.
        sourceCodec.upgradeFrom(ctx);
        upgradeCodec.upgradeTo(ctx, request);

        // Notify that the upgrade has occurred.
        ctx.fireUserEventTriggered(new UpgradeEvent(upgradeProtocol, request));

        // Remove this handler from the pipeline.
        ctx.pipeline().remove(HttpServerUpgradeHandler.this);

        // Add the listener last to avoid firing upgrade logic after
        // the channel is already closed since the listener may fire
        // immediately if the write failed eagerly.
        writeComplete.addListener(ctx.channel(), ChannelFutureListeners.CLOSE_ON_FAILURE);
        return true;
    }

    /**
     * Creates the 101 Switching Protocols response message.
     */
    private FullHttpResponse createUpgradeResponse(BufferAllocator allocator, CharSequence upgradeProtocol) {
        DefaultFullHttpResponse res = new DefaultFullHttpResponse(
                HTTP_1_1, SWITCHING_PROTOCOLS, allocator.allocate(0), validateHeaders);
        res.headers().add(HttpHeaderNames.CONNECTION, HttpHeaderValues.UPGRADE);
        res.headers().add(HttpHeaderNames.UPGRADE, upgradeProtocol);
        return res;
    }

    /**
     * Splits a comma-separated header value. The returned set is case-insensitive and contains each
     * part with whitespace removed.
     */
    private static List<CharSequence> splitHeader(CharSequence header) {
        final StringBuilder builder = new StringBuilder(header.length());
        final List<CharSequence> protocols = new ArrayList<>(4);
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
