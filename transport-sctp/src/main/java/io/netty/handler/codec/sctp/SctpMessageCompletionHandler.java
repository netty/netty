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
    private static final int DEFAULT_MAX_BUFFERED_BYTES = 16 * 1024 * 1024;

    private final IntObjectMap<List<ByteBuf>> incompleteSctpMessages = new IntObjectHashMap<>();
    private final int maxIncompleteSctpMessages;
    private final int maxFragments;
    private final int maxBufferedBytes;
    private long bufferedBytes;

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
        this(maxIncompleteSctpMessages, maxFragments, DEFAULT_MAX_BUFFERED_BYTES);
    }

    /**
     * Create a new instance.
     *
     * @param maxIncompleteSctpMessages the maximum number of incomplete sctp message inflight.
     * @param maxFragments              the maximum number of fragments per sctp message.
     * @param maxBufferedBytes          the maximum number of bytes buffered by incomplete sctp messages.
     */
    public SctpMessageCompletionHandler(int maxIncompleteSctpMessages, int maxFragments, int maxBufferedBytes) {
        super(SctpMessage.class);
        this.maxIncompleteSctpMessages = checkPositive(maxIncompleteSctpMessages, "maxIncompleteSctpMessages");
        this.maxFragments = checkPositive(maxFragments, "maxFragments");
        this.maxBufferedBytes = checkPositive(maxBufferedBytes, "maxBufferedBytes");
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
                checkBufferedBytes(byteBuf);
                //first incomplete message
                frag = new ArrayList<>();
                frag.add(byteBuf.retain());
                bufferedBytes += byteBuf.readableBytes();
                incompleteSctpMessages.put(streamIdentifier, frag);
            }
        } else {
            if (maxFragments <= frag.size()) {
                throw new CodecException("Too many fragments for sctp message: " + maxFragments);
            }
            checkBufferedBytes(byteBuf);
            frag.add(byteBuf.retain());
            bufferedBytes += byteBuf.readableBytes();
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
                removeBufferedBytes(frag);
            }
        }
    }

    private void checkBufferedBytes(ByteBuf byteBuf) {
        int readableBytes = byteBuf.readableBytes();
        if (readableBytes > maxBufferedBytes - bufferedBytes) {
            throw new CodecException("Too many buffered bytes for incomplete sctp messages: " + maxBufferedBytes);
        }
    }

    private void removeBufferedBytes(List<ByteBuf> buffers) {
        for (ByteBuf buffer : buffers) {
            bufferedBytes -= buffer.readableBytes();
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
        bufferedBytes = 0;
        super.handlerRemoved(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
        ctx.close();
    }
}
