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
package io.netty.handler.codec;

import io.netty.buffer.MessageBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.Packet;

/**
 * {@link MessageToMessageDecoder} which decodes from one {@link Packet} to an other message and automaticly free up
 * resources which were acquired by the {@link Packet}.
 *
 * For example here is an implementation which decodes a {@link Packet} to an {@link Integer} which represent
 * the length of the {@link Packet}.
 *
 * <pre>
 *     public class PacketToIntegerDecoder extends
 *             {@link MessageToMessageDecoder}&lt;{@link Packet},{@link Integer}&gt; {
 *         public PacketToIntegerDecoder() {
 *             super(Packet.class);
 *         }
 *
 *         {@code @Override}
 *         public {@link Integer} decodePacket({@link ChannelHandlerContext} ctx, {@link Packet} packet)
 *                 throws {@link Exception} {
 *             return packet.data().readableBytes());
 *         }
 *     }
 * </pre>
 *
 * @param <I>   the type of the input {@link Packet}
 * @param <O>   the type of the output message
 */
public abstract class PacketToMessageDecoder<I extends Packet, O> extends MessageToMessageDecoder<I, O> {
    protected PacketToMessageDecoder(Class<? extends I>... classes) {
        super(classes);
    }

    @Override
    protected final O decode(ChannelHandlerContext ctx, I msg) throws Exception {
        try {
            return decodePacket(ctx, msg);
        } finally {
            msg.free();
        }
    }

    /**
     * Decode from one message to an other. This method will be called till either the {@link MessageBuf} has
     * nothing left or till this method returns {@code null}.
     *
     * After this method returns {@link Packet#free()} is called to free up any resources. So if you need to save it
     * for later usage you will need to make a safe copy of it via {@link Packet#copy()}.
     *
     * @param ctx           the {@link ChannelHandlerContext} which this {@link MessageToMessageDecoder} belongs to
     * @param packet        the {@link Packet} to decode to an other message
     * @return message      the decoded message or {@code null} if more messages are needed be cause the implementation
     *                      needs to do some kind of aggragation
     * @throws Exception    is thrown if an error accour
     */
    protected abstract O decodePacket(ChannelHandlerContext ctx, I packet) throws Exception;
}
