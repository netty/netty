/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.sockjs.util;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.CharsetUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;

public final class JsonUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonUtil() {
    }

    @SuppressWarnings("resource")
    public static String[] decode(final TextWebSocketFrame frame)
            throws JsonParseException, JsonMappingException, IOException {
        final ByteBuf content = frame.content();
        if (content.readableBytes() == 0) {
            return new String[]{};
        }
        final ByteBufInputStream byteBufInputStream = new ByteBufInputStream(content);
        final byte firstByte = content.getByte(0);
        if (firstByte == '[') {
            return MAPPER.readValue(byteBufInputStream, String[].class);
        } else if (firstByte == '{') {
            return new String[]{content.toString(CharsetUtil.UTF_8)};
        } else {
            return new String[]{MAPPER.readValue(byteBufInputStream, String.class)};
        }
    }

    public static String[] decode(final String content) throws JsonParseException, JsonMappingException, IOException {
        final JsonNode root = MAPPER.readTree(content);
        if (root.isObject()) {
            return new String[]{root.toString()};
        }

        if (root.isValueNode()) {
            return new String[]{root.asText()};
        }

        if (!root.isArray()) {
            throw new JsonMappingException("content must be a JSON Array but was : " + content);
        }
        final List<String> messages = new ArrayList<String>();
        final Iterator<JsonNode> elements = root.getElements();
        while (elements.hasNext()) {
            final JsonNode field = elements.next();
            if (field.isValueNode()) {
                messages.add(field.asText());
            } else {
                messages.add(field.toString());
            }
        }
        return messages.toArray(new String[]{});
    }

}
