/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.handler.codec.sctp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.sctp.SctpMessage;
import io.netty.handler.codec.MessageToMessageDecoder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link MessageToMessageDecoder} which will take care of handle fragmented {@link SctpMessage}s, so
 * only <strong>complete</strong> {@link SctpMessage}s will be forwarded to the next
 * {@link ChannelInboundHandler}.
 */
public class SctpMessageCompletionHandler extends MessageToMessageDecoder<SctpMessage> {
    private final Map<Integer, ByteBuf> fragments = new HashMap<Integer, ByteBuf>();

    @Override
    protected void decode(ChannelHandlerContext ctx, SctpMessage msg, List<Object> out) throws Exception {
        final ByteBuf byteBuf = msg.content();
        final int protocolIdentifier = msg.protocolIdentifier();
        final int streamIdentifier = msg.streamIdentifier();
        final boolean isComplete = msg.isComplete();
        final boolean isUnordered = msg.isUnordered();

        ByteBuf frag = fragments.remove(streamIdentifier);
        if (frag == null) {
            frag = Unpooled.EMPTY_BUFFER;
        }

        if (isComplete && !frag.isReadable()) {
            //data chunk is not fragmented
            out.add(msg);
        } else if (!isComplete && frag.isReadable()) {
            //more message to complete
            fragments.put(streamIdentifier, Unpooled.wrappedBuffer(frag, byteBuf));
        } else if (isComplete && frag.isReadable()) {
            //last message to complete
            SctpMessage assembledMsg = new SctpMessage(
                    protocolIdentifier,
                    streamIdentifier,
                    isUnordered,
                    Unpooled.wrappedBuffer(frag, byteBuf));
            out.add(assembledMsg);
        } else {
            //first incomplete message
            fragments.put(streamIdentifier, byteBuf);
        }
        byteBuf.retain();
    }
}
