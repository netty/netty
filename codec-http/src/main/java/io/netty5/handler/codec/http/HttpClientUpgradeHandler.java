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

import io.netty5.buffer.api.Send;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.util.AsciiString;
import io.netty5.util.concurrent.Future;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import static io.netty5.handler.codec.http.HttpResponseStatus.SWITCHING_PROTOCOLS;
import static java.util.Objects.requireNonNull;

/**
 * Client-side handler for handling an HTTP upgrade handshake to another protocol. When the first
 * HTTP request is sent, this handler will add all appropriate headers to perform an upgrade to the
 * new protocol. If the upgrade fails (i.e. response is not 101 Switching Protocols), this handler
 * simply removes itself from the pipeline. If the upgrade is successful, upgrades the pipeline to
 * the new protocol.
 */
public class HttpClientUpgradeHandler<C extends HttpContent<C>> extends HttpObjectAggregator<C> {

    /**
     * User events that are fired to notify about upgrade status.
     */
    public enum UpgradeEvent {
        /**
         * The Upgrade request was sent to the server.
         */
        UPGRADE_ISSUED,

        /**
         * The Upgrade to the new protocol was successful.
         */
        UPGRADE_SUCCESSFUL,

        /**
         * The Upgrade was unsuccessful due to the server not issuing
         * with a 101 Switching Protocols response.
         */
        UPGRADE_REJECTED
    }

    /**
     * The source codec that is used in the pipeline initially.
     */
    public interface SourceCodec {

        /**
         * Removes or disables the encoder of this codec so that the {@link UpgradeCodec} can send an initial greeting
         * (if any).
         */
        void prepareUpgradeFrom(ChannelHandlerContext ctx);

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
         * Returns the name of the protocol supported by this codec, as indicated by the {@code 'UPGRADE'} header.
         */
        CharSequence protocol();

        /**
         * Sets any protocol-specific headers required to the upgrade request. Returns the names of
         * all headers that were added. These headers will be used to populate the CONNECTION header.
         */
        Collection<CharSequence> setUpgradeHeaders(ChannelHandlerContext ctx, HttpRequest upgradeRequest);

        /**
         * Performs an HTTP protocol upgrade from the source codec. This method is responsible for
         * adding all handlers required for the new protocol.
         *
         * @param ctx the context for the current handler.
         * @param upgradeResponse the 101 Switching Protocols response that indicates that the server
         *            has switched to this protocol.
         */
        void upgradeTo(ChannelHandlerContext ctx, Send<FullHttpResponse> upgradeResponse) throws Exception;
    }

    private final SourceCodec sourceCodec;
    private final UpgradeCodec upgradeCodec;
    private boolean upgradeRequested;

    /**
     * Constructs the client upgrade handler.
     *
     * @param sourceCodec the codec that is being used initially.
     * @param upgradeCodec the codec that the client would like to upgrade to.
     * @param maxContentLength the maximum length of the aggregated content.
     */
    public HttpClientUpgradeHandler(SourceCodec sourceCodec, UpgradeCodec upgradeCodec,
                                    int maxContentLength) {
        super(maxContentLength);
        requireNonNull(sourceCodec, "sourceCodec");
        requireNonNull(upgradeCodec, "upgradeCodec");
        this.sourceCodec = sourceCodec;
        this.upgradeCodec = upgradeCodec;
    }

    @Override
    public Future<Void> write(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof HttpRequest)) {
            return ctx.write(msg);
        }

        if (upgradeRequested) {
            return ctx.newFailedFuture(new IllegalStateException(
                    "Attempting to write HTTP request with upgrade in progress"));
        }

        upgradeRequested = true;
        setUpgradeRequestHeaders(ctx, (HttpRequest) msg);

        // Continue writing the request.
        Future<Void> f = ctx.write(msg);

        // Notify that the upgrade request was issued.
        ctx.fireUserEventTriggered(UpgradeEvent.UPGRADE_ISSUED);
        // Now we wait for the next HTTP response to see if we switch protocols.
        return f;
    }

    @Override
    protected void decode(final ChannelHandlerContext ctx, HttpObject msg)
            throws Exception {
        FullHttpResponse response = null;
        try {
            if (!upgradeRequested) {
                throw new IllegalStateException("Read HTTP response without requesting protocol switch");
            }

            if (msg instanceof HttpResponse) {
                HttpResponse rep = (HttpResponse) msg;
                if (!SWITCHING_PROTOCOLS.equals(rep.status())) {
                    // The server does not support the requested protocol, just remove this handler
                    // and continue processing HTTP.
                    // NOTE: not releasing the response since we're letting it propagate to the
                    // next handler.
                    ctx.fireUserEventTriggered(UpgradeEvent.UPGRADE_REJECTED);
                    ctx.fireChannelRead(msg);
                    removeThisHandler(ctx);
                    return;
                }
            }

            if (msg instanceof FullHttpResponse) {
                response = (FullHttpResponse) msg;

                tryUpgrade(ctx, response);
            } else {
                // Call the base class to handle the aggregation of the full request.
                super.decode(new DelegatingChannelHandlerContext(ctx) {
                    @Override
                    public ChannelHandlerContext fireChannelRead(Object msg) {
                        FullHttpResponse response = (FullHttpResponse) msg;
                        tryUpgrade(ctx, response);
                        return this;
                    }
                }, msg);
            }

        } catch (Throwable t) {
            if (response != null && response.isAccessible()) {
                response.close();
            }
            ctx.fireExceptionCaught(t);
            removeThisHandler(ctx);
        }
    }

    private void tryUpgrade(ChannelHandlerContext ctx, FullHttpResponse response) {
        try (response) {
            CharSequence upgradeHeader = response.headers().get(HttpHeaderNames.UPGRADE);
            if (upgradeHeader != null && !AsciiString.contentEqualsIgnoreCase(upgradeCodec.protocol(), upgradeHeader)) {
                throw new IllegalStateException(
                        "Switching Protocols response with unexpected UPGRADE protocol: " + upgradeHeader);
            }

            // Upgrade to the new protocol.
            sourceCodec.prepareUpgradeFrom(ctx);
            upgradeCodec.upgradeTo(ctx, response.send());

            // Notify that the upgrade to the new protocol completed successfully.
            ctx.fireUserEventTriggered(UpgradeEvent.UPGRADE_SUCCESSFUL);

            // We guarantee UPGRADE_SUCCESSFUL event will be arrived at the next handler
            // before http2 setting frame and http response.
            sourceCodec.upgradeFrom(ctx);
            removeThisHandler(ctx);
        } catch (Throwable t) {
            ctx.fireExceptionCaught(t);
            removeThisHandler(ctx);
        }
    }

    private static void removeThisHandler(ChannelHandlerContext ctx) {
        ctx.pipeline().remove(ctx.name());
    }

    /**
     * Adds all upgrade request headers necessary for an upgrade to the supported protocols.
     */
    private void setUpgradeRequestHeaders(ChannelHandlerContext ctx, HttpRequest request) {
        // Set the UPGRADE header on the request.
        request.headers().set(HttpHeaderNames.UPGRADE, upgradeCodec.protocol());

        // Add all protocol-specific headers to the request.
        Set<CharSequence> connectionParts = new LinkedHashSet<>(2);
        connectionParts.addAll(upgradeCodec.setUpgradeHeaders(ctx, request));

        // Set the CONNECTION header from the set of all protocol-specific headers that were added.
        StringBuilder builder = new StringBuilder();
        for (CharSequence part : connectionParts) {
            builder.append(part);
            builder.append(',');
        }
        builder.append(HttpHeaderValues.UPGRADE);
        request.headers().add(HttpHeaderNames.CONNECTION, builder.toString());
    }
}
