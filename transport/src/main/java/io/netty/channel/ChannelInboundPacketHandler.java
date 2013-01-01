/*
 * Copyright 2012 The Netty Project
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
package io.netty.channel;

/**
 * {@link ChannelHandler} which handles inbound {@link Packet}s and automaticly free up resources which were acquired
 * by the {@link Packet}.
 *
 * <pre>
 *     public interface MyPacket extends {@link Packet} {
 *         ....
 *     }
 *
 *     public class MyPacketHandler extends
 *             {@link ChannelInboundMessageHandlerAdapter}&lt;MyPacket&gt; {
 *         public MyPacketHandler() {
 *             super(MyPacket.class);
 *         }
 *
 *         {@code @Override}
 *         public void packetReceived({@link ChannelHandlerContext} ctx, MyPacket packet)
 *                 throws {@link Exception} {
 *             // Do something with the packet
 *             ...
 *             ...
 *         }
 *     }
 * </pre>
 *
 * @param <P>   The type of the {@link Packet} to handle
 */
public abstract class ChannelInboundPacketHandler<P extends Packet> extends ChannelInboundMessageHandlerAdapter<P> {

    protected ChannelInboundPacketHandler(Class<? extends P>... classes) {
        super(classes);
    }

    @Override
    protected final void messageReceived(ChannelHandlerContext ctx, P packet) throws Exception {
        try {
            packetReceived(ctx, packet);
        } finally {
            packet.free();
        }
    }

    /**
     * Is called once a {@link Packet} was received.
     *
     * After this method returns {@link Packet#free()} is called to free up any resources. So if you need to save it
     * for later usage you will need to make a safe copy of it via {@link Packet#copy()}.
     *
     * @param ctx           the {@link ChannelHandlerContext} which this {@link ChannelHandler} belongs to
     * @param packet        the {@link Packet} to handle
     * @throws Exception    thrown when an error accour
     */
    protected abstract void packetReceived(ChannelHandlerContext ctx, P packet) throws Exception;
}
