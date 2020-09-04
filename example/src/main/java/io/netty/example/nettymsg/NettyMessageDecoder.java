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
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

public final class NettyMessageDecoder extends LengthFieldBasedFrameDecoder {

    public NettyMessageDecoder(int maxFrameLength, int lengthFieldOffset, int lengthFieldLength) {
        super(maxFrameLength, lengthFieldOffset, lengthFieldLength);
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        //        ByteBuf frame = (ByteBuf) super.decode(ctx, in);
        //        if (null == frame) {
        //            return null;
        //        }

        NettyMessage message = new NettyMessage();
        Header header = new Header();
        header.setCrcCode(in.readInt());
        header.setLength(in.readInt());
        header.setSessionId(in.readLong());
        header.setType(in.readByte());
        header.setPriority(in.readByte());

        Charset charset = Charset.forName("UTF-8");
        int size = in.readInt();
        if (size > 0) {
            Map<String, Object> attch = new HashMap<String, Object>();
            int keySize;
            int valueSize;
            byte[] keyArray;
            byte[] valueArray;
            String key;
            String value;
            for (int i = 0; i < size; i++) {
                keySize = in.readInt();
                keyArray = new byte[keySize];
                in.readBytes(keyArray);
                key = new String(keyArray, charset);

                valueSize = in.readInt();
                valueArray = new byte[valueSize];
                in.readBytes(valueArray);
                value = new String(valueArray, charset);
                int clazzSize = in.readInt();
                byte[] clazzArray = new byte[clazzSize];
                in.readBytes(clazzArray);
                String clazz = new String(clazzArray, charset);
                attch.put(key, GsonUtil.parse(value, Class.forName(clazz)));
            }
            header.setAttachment(attch);
        }
        if (in.readableBytes() > 4) {
            int bodyLength = in.readInt();
            byte[] bodyArray = new byte[bodyLength];
            in.readBytes(bodyArray);
            String bodyJson = new String(bodyArray, charset);
            int clazzSize = in.readInt();
            byte[] clazzArray = new byte[clazzSize];
            in.readBytes(clazzArray);
            String clazz = new String(clazzArray, charset);
            Object body = GsonUtil.parse(bodyJson, Class.forName(clazz));
            message.setBody(body);
        }
        message.setHeader(header);
        return message;
    }
}
