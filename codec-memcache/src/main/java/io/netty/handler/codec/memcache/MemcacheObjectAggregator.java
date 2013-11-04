/*
 * Copyright 2013 The Netty Project
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
package io.netty.handler.codec.memcache;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.memcache.binary.BinaryMemcacheRequestDecoder;
import io.netty.handler.codec.memcache.binary.BinaryMemcacheResponseEncoder;

/**
 * A {@link ChannelHandler} that aggregates an {@link MemcacheMessage}
 * and its following {@link MemcacheContent}s into a single {@link MemcacheMessage} with
 * no following {@link MemcacheContent}s.  It is useful when you don't want to take
 * care of memcache messages where the content comes along in chunks. Insert this
 * handler after a MemcacheObjectDecoder in the {@link ChannelPipeline}.
 * <p/>
 * For example, here for the binary protocol:
 * <p/>
 * <pre>
 * {@link ChannelPipeline} p = ...;
 * ...
 * p.addLast("decoder", new {@link BinaryMemcacheRequestDecoder}());
 * p.addLast("aggregator", <b>new {@link MemcacheObjectAggregator}(1048576)</b>);
 * ...
 * p.addLast("encoder", new {@link BinaryMemcacheResponseEncoder}());
 * p.addLast("handler", new YourMemcacheRequestHandler());
 * </pre>
 */
public abstract class MemcacheObjectAggregator extends MessageToMessageDecoder<MemcacheObject> {

    /**
     * Contains the current message that gets aggregated.
     */
    protected FullMemcacheMessage currentMessage;

    /**
     * Holds the current channel handler context if set.
     */
    protected ChannelHandlerContext ctx;

    public static final int DEFAULT_MAX_COMPOSITEBUFFER_COMPONENTS = 1024;

    private int maxCumulationBufferComponents = DEFAULT_MAX_COMPOSITEBUFFER_COMPONENTS;

    private final int maxContentLength;

    protected MemcacheObjectAggregator(int maxContentLength) {
        if (maxContentLength <= 0) {
            throw new IllegalArgumentException("maxContentLength must be a positive integer: " + maxContentLength);
        }

        this.maxContentLength = maxContentLength;
    }

    /**
     * Returns the maximum number of components in the cumulation buffer.  If the number of
     * the components in the cumulation buffer exceeds this value, the components of the
     * cumulation buffer are consolidated into a single component, involving memory copies.
     * The default value of this property is {@link #DEFAULT_MAX_COMPOSITEBUFFER_COMPONENTS}.
     */
    public final int getMaxCumulationBufferComponents() {
        return maxCumulationBufferComponents;
    }

    /**
     * Sets the maximum number of components in the cumulation buffer.  If the number of
     * the components in the cumulation buffer exceeds this value, the components of the
     * cumulation buffer are consolidated into a single component, involving memory copies.
     * The default value of this property is {@link #DEFAULT_MAX_COMPOSITEBUFFER_COMPONENTS}
     * and its minimum allowed value is {@code 2}.
     */
    public final void setMaxCumulationBufferComponents(int maxCumulationBufferComponents) {
        if (maxCumulationBufferComponents < 2) {
            throw new IllegalArgumentException(
                "maxCumulationBufferComponents: " + maxCumulationBufferComponents +
                    " (expected: >= 2)");
        }

        if (ctx == null) {
            this.maxCumulationBufferComponents = maxCumulationBufferComponents;
        } else {
            throw new IllegalStateException(
                "decoder properties cannot be changed once the decoder is added to a pipeline.");
        }
    }

    public int getMaxContentLength() {
        return maxContentLength;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);

        if (currentMessage != null) {
            currentMessage.release();
            currentMessage = null;
        }
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        this.ctx = ctx;
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        super.handlerRemoved(ctx);

        if (currentMessage != null) {
            currentMessage.release();
            currentMessage = null;
        }
    }

}
