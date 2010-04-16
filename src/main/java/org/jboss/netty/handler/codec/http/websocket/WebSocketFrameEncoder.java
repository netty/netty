/*
 * Copyright 2010 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.handler.codec.http.websocket;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelHandler.Sharable;
import org.jboss.netty.handler.codec.oneone.OneToOneEncoder;

/**
 * Encodes a {@link WebSocketFrame} into a {@link ChannelBuffer}.
 * <p>
 * For the detailed instruction on adding add Web Socket support to your HTTP
 * server, take a look into the <tt>WebSocketServer</tt> example located in the
 * {@code org.jboss.netty.example.http.websocket} package.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author Mike Heath (mheath@apache.org)
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev$, $Date$
 *
 * @apiviz.landmark
 * @apiviz.uses org.jboss.netty.handler.codec.http.websocket.WebSocketFrame
 */
@Sharable
public class WebSocketFrameEncoder extends OneToOneEncoder {

    @Override
    protected Object encode(ChannelHandlerContext ctx, Channel channel, Object msg) throws Exception {
        if (msg instanceof WebSocketFrame) {
            WebSocketFrame frame = (WebSocketFrame) msg;
            int type = frame.getType();
            if (frame.isText()) {
                // Text frame
                ChannelBuffer data = frame.getBinaryData();
                ChannelBuffer encoded =
                    channel.getConfig().getBufferFactory().getBuffer(
                            data.order(), data.readableBytes() + 2);
                encoded.writeByte((byte) type);
                encoded.writeBytes(data, data.readableBytes());
                encoded.writeByte((byte) 0xFF);
                return encoded;
            } else {
                // Binary frame
                ChannelBuffer data = frame.getBinaryData();
                int dataLen = data.readableBytes();
                ChannelBuffer encoded =
                    channel.getConfig().getBufferFactory().getBuffer(
                            data.order(), dataLen + 5);
                encoded.writeByte((byte) type);
                encoded.writeByte((byte) (dataLen >>> 28 & 0x7F | 0x80));
                encoded.writeByte((byte) (dataLen >>> 14 & 0x7F | 0x80));
                encoded.writeByte((byte) (dataLen >>> 7 & 0x7F | 0x80));
                encoded.writeByte((byte) (dataLen & 0x7F));
                encoded.writeBytes(data, dataLen);
                return encoded;
            }
        }
        return msg;
    }
}
