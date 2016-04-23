/*
 * Copyright 2016 The Netty Project
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
package io.netty.handler.codec;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.ChannelPromiseAggregatorFactory;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.TypeParameterMatcher;

import static io.netty.channel.DefaultChannelPromiseAggregatorFactory.newInstance;

public abstract class TypeSensitiveMessageEncoder<T> extends ChannelOutboundHandlerAdapter {
    private final TypeParameterMatcher matcher;

    /**
     * Create a new instance which will try to detect the types to match out of the type parameter of the class.
     */
    protected TypeSensitiveMessageEncoder() {
        matcher = TypeParameterMatcher.find(this, TypeSensitiveMessageEncoder.class, "T");
    }

    /**
     * Create a new instance
     * @param outboundMessageType The type of messages to match and so encode
     */
    protected TypeSensitiveMessageEncoder(Class<? extends T> outboundMessageType) {
        matcher = TypeParameterMatcher.get(outboundMessageType);
    }

    /**
     * Returns {@code true} if the given message should be handled. If {@code false} it will be passed to the next
     * {@link ChannelOutboundHandler} in the {@link ChannelPipeline}.
     */
    protected boolean acceptOutboundMessage(Object msg) throws Exception {
        return matcher.match(msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (acceptOutboundMessage(msg)) {
            ChannelPromiseAggregatorFactory aggregatorFactory = newInstance(promise, ctx.executor());
            try {
                encode(ctx, (T) msg, aggregatorFactory);
                aggregatorFactory.doneAllocatingPromises();
            } catch (EncoderException e) {
                throw e;
            } catch (Throwable t) {
                throw new EncoderException(t);
            } finally {
                ReferenceCountUtil.release(msg);
            }
        } else {
            ctx.write(msg, promise);
        }
    }

    /**
     * Encode from one message to an other. This method will be called for each written message that can be handled
     * by this encoder.
     *
     * @param ctx the {@link ChannelHandlerContext} which this handler belongs to.
     * @param msg the message to encode to an other one.
     * @param promiseFactory Used to aggregate all write operation from
     * {@link #write(ChannelHandlerContext, Object, ChannelPromise)}.
     * @throws Exception is thrown if an error occurs.
     */
    protected abstract void encode(ChannelHandlerContext ctx, T msg, ChannelPromiseAggregatorFactory promiseFactory)
            throws Exception;
}
