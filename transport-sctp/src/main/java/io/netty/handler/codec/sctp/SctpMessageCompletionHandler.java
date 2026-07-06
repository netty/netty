/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.handler.codec.sctp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.sctp.SctpMessage;
import io.netty.handler.codec.CodecException;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;

import java.util.ArrayList;
import java.util.List;

import static io.netty.util.internal.ObjectUtil.checkPositive;

/**
 * {@link MessageToMessageDecoder} which will take care of handle fragmented {@link SctpMessage}s, so
 * only <strong>complete</strong> {@link SctpMessage}s will be forwarded to the next
 * {@link ChannelInboundHandler}.
 */
public class SctpMessageCompletionHandler extends MessageToMessageDecoder<SctpMessage> {
    private final IntObjectMap<List<ByteBuf>> incompleteSctpMessages = new IntObjectHashMap<>();
    private final int maxIncompleteSctpMessages;
    private final int maxFragments;

    public SctpMessageCompletionHandler() {
        this(128, 128);
    }

    /**
     * Create a new instance.
     *
     * @param maxIncompleteSctpMessages the maximum number of incomplete sctp message inflight.
     * @param maxFragments              the maximum number of fragments per sctp message.
     */
    public SctpMessageCompletionHandler(int maxIncompleteSctpMessages, int maxFragments) {
        super(SctpMessage.class);
        this.maxIncompleteSctpMessages = checkPositive(maxIncompleteSctpMessages, "maxIncompleteSctpMessages");
        this.maxFragments = checkPositive(maxFragments, "maxFragments");
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, SctpMessage msg, List<Object> out) throws Exception {
        final ByteBuf byteBuf = msg.content();
        final int protocolIdentifier = msg.protocolIdentifier();
        final int streamIdentifier = msg.streamIdentifier();
        final boolean isComplete = msg.isComplete();
        final boolean isUnordered = msg.isUnordered();

        List<ByteBuf> frag = incompleteSctpMessages.get(streamIdentifier);
        if (frag == null) {
            // No previous fragments.
            if (isComplete) {
                out.add(msg.retain());
            } else {
                if (maxIncompleteSctpMessages <= incompleteSctpMessages.size()) {
                    throw new CodecException(
                            "Too many incomplete sctp messages in flight: " + maxIncompleteSctpMessages);
                }
                //first incomplete message
                frag = new ArrayList<>();
                frag.add(byteBuf.retain());
                incompleteSctpMessages.put(streamIdentifier, frag);
            }
        } else {
            if (maxFragments <= frag.size()) {
                throw new CodecException("Too many fragments for sctp message: " + maxFragments);
            }
            frag.add(byteBuf.retain());
            if (isComplete) {
                // Is complete so remove it.
                incompleteSctpMessages.remove(streamIdentifier);
                CompositeByteBuf composite = ctx.alloc().compositeBuffer();

                for (int i = 0; i < frag.size(); i++) {
                    composite.addComponent(true, frag.get(i));
                }
                // last message to complete
                SctpMessage assembledMsg = new SctpMessage(
                        protocolIdentifier,
                        streamIdentifier,
                        isUnordered,
                        composite);
                out.add(assembledMsg);
            }
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        for (List<ByteBuf> buffers: incompleteSctpMessages.values()) {
            for (ByteBuf buffer: buffers) {
                buffer.release();
            }
        }
        incompleteSctpMessages.clear();
        super.handlerRemoved(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
        ctx.close();
    }
}
