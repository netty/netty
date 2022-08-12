/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty5.handler.codec;

import io.netty5.buffer.api.Buffer;
import io.netty5.channel.AddressedEnvelope;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelPipeline;
import io.netty5.channel.ReadBufferAllocator;
import io.netty5.channel.socket.DatagramPacket;
import io.netty5.handler.codec.bytes.ByteArrayEncoder;
import io.netty5.util.concurrent.Future;
import io.netty5.util.internal.StringUtil;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * An encoder that encodes the content in {@link AddressedEnvelope} to {@link DatagramPacket} using
 * the specified message encoder. E.g.,
 *
 * <pre><code>
 * {@link ChannelPipeline} pipeline = ...;
 * pipeline.addLast("udpEncoder", new {@link DatagramPacketEncoder}(new {@link ByteArrayEncoder}(...));
 * </code></pre>
 *
 * Note: As UDP packets are out-of-order, you should make sure the encoded message size are not greater than
 * the max safe packet size in your particular network path which guarantees no packet fragmentation.
 *
 * @param <M> the type of message to be encoded
 */
public class DatagramPacketEncoder<M> extends MessageToMessageEncoder<AddressedEnvelope<M, InetSocketAddress>> {

    private final MessageToMessageEncoder<? super M> encoder;

    /**
     * Create an encoder that encodes the content in {@link AddressedEnvelope} to {@link DatagramPacket} using
     * the specified message encoder.
     *
     * @param encoder the specified message encoder
     */
    public DatagramPacketEncoder(MessageToMessageEncoder<? super M> encoder) {
        this.encoder = requireNonNull(encoder, "encoder");
    }

    @Override
    public boolean acceptOutboundMessage(Object msg) throws Exception {
        if (super.acceptOutboundMessage(msg)) {
            @SuppressWarnings("rawtypes")
            AddressedEnvelope envelope = (AddressedEnvelope) msg;
            return encoder.acceptOutboundMessage(envelope.content())
                    && (envelope.sender() instanceof InetSocketAddress || envelope.sender() == null)
                    && envelope.recipient() instanceof InetSocketAddress;
        }
        return false;
    }

    @Override
    protected void encode(
            ChannelHandlerContext ctx, AddressedEnvelope<M, InetSocketAddress> msg, List<Object> out) throws Exception {
        assert out.isEmpty();

        encoder.encode(ctx, msg.content(), out);
        if (out.size() != 1) {
            throw new EncoderException(
                    StringUtil.simpleClassName(encoder) + " must produce only one message.");
        }
        Object content = out.get(0);
        if (content instanceof Buffer) {
            // Replace the Buffer with a DatagramPacket.
            out.set(0, new DatagramPacket((Buffer) content, msg.recipient(), msg.sender()));
        } else {
            throw new EncoderException(
                    StringUtil.simpleClassName(encoder) + " must produce only ByteBuf.");
        }
    }

    @Override
    public Future<Void> bind(ChannelHandlerContext ctx, SocketAddress localAddress) {
        return encoder.bind(ctx, localAddress);
    }

    @Override
    public Future<Void> connect(
            ChannelHandlerContext ctx, SocketAddress remoteAddress,
            SocketAddress localAddress) {
        return encoder.connect(ctx, remoteAddress, localAddress);
    }

    @Override
    public Future<Void> disconnect(ChannelHandlerContext ctx) {
        return encoder.disconnect(ctx);
    }

    @Override
    public Future<Void> close(ChannelHandlerContext ctx) {
        return encoder.close(ctx);
    }

    @Override
    public Future<Void> deregister(ChannelHandlerContext ctx) {
        return encoder.deregister(ctx);
    }

    @Override
    public void read(ChannelHandlerContext ctx, ReadBufferAllocator readBufferAllocator) {
        encoder.read(ctx, readBufferAllocator);
    }

    @Override
    public void flush(ChannelHandlerContext ctx) {
        encoder.flush(ctx);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        encoder.handlerAdded(ctx);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        encoder.handlerRemoved(ctx);
    }

    @Override
    public boolean isSharable() {
        return encoder.isSharable();
    }
}
