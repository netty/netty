/*
 *
 *  * Copyright 2020 The Netty Project
 *  *
 *  * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 *  * "License"); you may not use this file except in compliance with the License. You may obtain a
 *  * copy of the License at:
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software distributed under the License
 *  * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 *  * or implied. See the License for the specific language governing permissions and limitations under
 *  * the License.
 *
 */

package io.netty.example.nettymsg;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

public final class NettyMessageEncoder extends MessageToMessageEncoder<NettyMessage> {
    @Override
    protected void encode(ChannelHandlerContext ctx, NettyMessage msg, List<Object> out) throws Exception {
        if (null == msg || msg.getHeader() == null) {
            throw new Exception("encode message is null!");
        }

        ByteBuf sendBuf = Unpooled.buffer();
        sendBuf.writeInt(msg.getHeader().getCrcCode())
                .writeInt(msg.getHeader().getLength())
                .writeLong(msg.getHeader().getSessionId())
                .writeByte(msg.getHeader().getType())
                .writeByte(msg.getHeader().getPriority())
                .writeInt(msg.getHeader().getAttachment().size());
        Charset charset = Charset.forName("UTF-8");
        byte[] keyArray;
        byte[] valueArray;
        for (Map.Entry<String, Object> entry : msg.getHeader().getAttachment().entrySet()) {
            keyArray = entry.getKey().getBytes(charset);
            sendBuf.writeInt(keyArray.length);
            sendBuf.writeBytes(keyArray);
            valueArray = GsonUtil.toJson(entry.getValue()).getBytes(charset);
            sendBuf.writeInt(valueArray.length);
            sendBuf.writeBytes(valueArray);
            byte[] clazzArray = entry.getValue().getClass().getName().getBytes();
            sendBuf.writeInt(clazzArray.length);
            sendBuf.writeBytes(clazzArray);
        }
        if (msg.getBody() != null) {
            byte[] bodyArray = GsonUtil.toJson(msg.getBody()).getBytes(charset);
            sendBuf.writeInt(bodyArray.length);
            sendBuf.writeBytes(bodyArray);
            byte[] clazzArray = msg.getBody().getClass().getName().getBytes();
            sendBuf.writeInt(clazzArray.length);
            sendBuf.writeBytes(clazzArray);
        }
        //sendBuf.writeInt(0);
        sendBuf.setInt(4, sendBuf.readableBytes());
        out.add(sendBuf);
    }
}
