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

import static io.netty.handler.codec.http.HttpHeaders.Names.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaders.Names.UPGRADE;
import static io.netty.handler.codec.http.HttpResponseStatus.SWITCHING_PROTOCOLS;
import static io.netty.util.ReferenceCountUtil.release;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Client-side handler for handling an HTTP upgrade handshake to another protocol. When the first
 * HTTP request is sent, this handler will add all appropriate headers to perform an upgrade to the
 * new protocol. If the upgrade fails (i.e. response is not 101 Switching Protocols), this handler
 * simply removes itself from the pipeline. If the upgrade is successful, upgrades the pipeline to
 * the new protocol.
 */
public class HttpClientUpgradeHandler extends HttpObjectAggregator {

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
         * Removes this codec (i.e. all associated handlers) from the pipeline.
         */
        void upgradeFrom(ChannelHandlerContext ctx);
    }

    /**
     * A codec that the source can be upgraded to.
     */
    public interface UpgradeCodec {
        /**
         * Returns the name of the protocol supported by this codec, as indicated by the {@link UPGRADE} header.
         */
        String protocol();

        /**
         * Sets any protocol-specific headers required to the upgrade request. Returns the names of
         * all headers that were added. These headers will be used to populate the CONNECTION header.
         */
        Collection<String> setUpgradeHeaders(ChannelHandlerContext ctx, HttpRequest upgradeRequest);

        /**
         * Performs an HTTP protocol upgrade from the source codec. This method is responsible for
         * adding all handlers required for the new protocol.
         *
         * @param ctx the context for the current handler.
         * @param upgradeResponse the 101 Switching Protocols response that indicates that the server
         *            has switched to this protocol.
         */
        void upgradeTo(ChannelHandlerContext ctx, FullHttpResponse upgradeResponse) throws Exception;
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
        if (sourceCodec == null) {
            throw new NullPointerException("sourceCodec");
        }
        if (upgradeCodec == null) {
            throw new NullPointerException("upgradeCodec");
        }
        this.sourceCodec = sourceCodec;
        this.upgradeCodec = upgradeCodec;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise)
            throws Exception {
        if (!(msg instanceof HttpRequest)) {
            super.write(ctx, msg, promise);
            return;
        }

        if (upgradeRequested) {
            promise.setFailure(new IllegalStateException(
                    "Attempting to write HTTP request with upgrade in progress"));
            return;
        }

        upgradeRequested = true;
        setUpgradeRequestHeaders(ctx, (HttpRequest) msg);

        // Continue writing the request.
        super.write(ctx, msg, promise);

        // Notify that the upgrade request was issued.
        ctx.fireUserEventTriggered(UpgradeEvent.UPGRADE_ISSUED);
        // Now we wait for the next HTTP response to see if we switch protocols.
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, HttpObject msg, List<Object> out)
            throws Exception {
        FullHttpResponse response = null;
        try {
            if (!upgradeRequested) {
                throw new IllegalStateException("Read HTTP response without requesting protocol switch");
            }

            if (msg instanceof FullHttpResponse) {
                response = (FullHttpResponse) msg;
                // Need to retain since the base class will release after returning from this method.
                response.retain();
                out.add(response);
            } else {
                // Call the base class to handle the aggregation of the full request.
                super.decode(ctx, msg, out);
                if (out.isEmpty()) {
                    // The full request hasn't been created yet, still awaiting more data.
                    return;
                }

                assert out.size() == 1;
                response = (FullHttpResponse) out.get(0);
            }

            if (!SWITCHING_PROTOCOLS.equals(response.status())) {
                // The server does not support the requested protocol, just remove this handler
                // and continue processing HTTP.
                // NOTE: not releasing the response since we're letting it propagate to the
                // next handler.
                ctx.fireUserEventTriggered(UpgradeEvent.UPGRADE_REJECTED);
                removeThisHandler(ctx);
                return;
            }

            String upgradeHeader = response.headers().get(UPGRADE);
            if (upgradeHeader == null) {
                throw new IllegalStateException(
                        "Switching Protocols response missing UPGRADE header");
            }
            if (!upgradeCodec.protocol().equalsIgnoreCase(upgradeHeader)) {
                throw new IllegalStateException(
                        "Switching Protocols response with unexpected UPGRADE protocol: "
                                + upgradeHeader);
            }

            // Upgrade to the new protocol.
            sourceCodec.upgradeFrom(ctx);
            upgradeCodec.upgradeTo(ctx, response);

            // Notify that the upgrade to the new protocol completed successfully.
            ctx.fireUserEventTriggered(UpgradeEvent.UPGRADE_SUCCESSFUL);

            // We switched protocols, so we're done with the upgrade response.
            // Release it and clear it from the output.
            response.release();
            out.clear();
            removeThisHandler(ctx);
        } catch (Throwable t) {
            release(response);
            ctx.fireExceptionCaught(t);
            removeThisHandler(ctx);
        }
    }

    private void removeThisHandler(ChannelHandlerContext ctx) {
        ctx.pipeline().remove(ctx.name());
    }

    /**
     * Adds all upgrade request headers necessary for an upgrade to the supported protocols.
     */
    private void setUpgradeRequestHeaders(ChannelHandlerContext ctx, HttpRequest request) {
        // Set the UPGRADE header on the request.
        request.headers().set(UPGRADE, upgradeCodec.protocol());

        // Add all protocol-specific headers to the request.
        Set<String> connectionParts = new LinkedHashSet<String>(2);
        connectionParts.addAll(upgradeCodec.setUpgradeHeaders(ctx, request));

        // Set the CONNECTION header from the set of all protocol-specific headers that were added.
        StringBuilder builder = new StringBuilder();
        for (String part : connectionParts) {
            builder.append(part);
            builder.append(",");
        }
        builder.append(UPGRADE);
        request.headers().set(CONNECTION, builder.toString());
    }
}
