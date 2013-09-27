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
package io.netty.handler.codec.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;

import java.util.List;

public class JsonNodeCodec extends MessageToMessageCodec<ByteBuf, Object> {
    private static volatile ObjectMapper objectMapper;
    private static final ObjectMapper defaultObjectMapper = new ObjectMapper();

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, List<Object> out) throws Exception {
        byte[] jsonBytes = getObjectMapper().writeValueAsBytes(msg);
        out.add(Unpooled.wrappedBuffer(jsonBytes));
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) throws Exception {
        final byte[] array;
        final int offset;
        final int length = msg.readableBytes();
        if (msg.hasArray()) {
            array = msg.array();
            offset = msg.arrayOffset() + msg.readerIndex();
        } else {
            array = new byte[length];
            msg.getBytes(msg.readerIndex(), array, 0, length);
            offset = 0;
        }
        JsonNode jsonNode = getObjectMapper().readValue(array, offset, length, JsonNode.class);
        out.add(jsonNode);
    }

    public static ObjectMapper getObjectMapper() {
        return objectMapper == null ? defaultObjectMapper : objectMapper;
    }

    public static void setObjectMapper(ObjectMapper objectMapper) {
        JsonNodeCodec.objectMapper = objectMapper;
    }
}
