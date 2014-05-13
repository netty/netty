/*
 * Copyright 2013 The Netty Project
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

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.io.CharTypes;
import com.fasterxml.jackson.core.json.JsonWriteContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.CharsetUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class JsonUtil {

    private static final ObjectMapper MAPPER;
    private static final String[] EMPTY_STRING_ARRAY = {};
    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();
    private static final int[] ESCAPE_CODES = CharTypes.get7BitOutputEscapes();

    static {
        MAPPER = new ObjectMapper();

        // This code adapted from Vert.x JsonCode.
        SimpleModule simpleModule = new SimpleModule("simplepush", new Version(0, 0, 8, null, "io.netty",
                "netty-codec-sockjs"));
        simpleModule.addSerializer(String.class, new JsonSerializer<String>() {

            private void writeUnicodeEscape(final JsonGenerator gen, final char c) throws IOException {
                gen.writeRaw('\\');
                gen.writeRaw('u');
                gen.writeRaw(HEX_CHARS[c >> 12 & 0xF]);
                gen.writeRaw(HEX_CHARS[c >> 8 & 0xF]);
                gen.writeRaw(HEX_CHARS[c >> 4 & 0xF]);
                gen.writeRaw(HEX_CHARS[c & 0xF]);
            }

            private void writeShortEscape(final JsonGenerator gen, final char c) throws IOException {
                gen.writeRaw('\\');
                gen.writeRaw(c);
            }

            @Override
            public void serialize(final String str, final JsonGenerator gen, final SerializerProvider provider)
                    throws IOException {
                final int status = ((JsonWriteContext) gen.getOutputContext()).writeValue();
                switch (status) {
                case JsonWriteContext.STATUS_OK_AFTER_COLON:
                    gen.writeRaw(':');
                    break;
                case JsonWriteContext.STATUS_OK_AFTER_COMMA:
                    gen.writeRaw(',');
                    break;
                case JsonWriteContext.STATUS_EXPECT_NAME:
                    throw new JsonGenerationException("Can not write string value here");
                }
                gen.writeRaw('"');
                for (char c : str.toCharArray()) {
                    if (c >= 0x80) {
                        writeUnicodeEscape(gen, c);
                    } else {
                        // use escape table for first 128 characters
                        int code = c < ESCAPE_CODES.length ? ESCAPE_CODES[c] : 0;
                        if (code == 0) {
                            gen.writeRaw(c); // no escaping
                        } else if (code == -1) {
                            writeUnicodeEscape(gen, c);
                        } else {
                            writeShortEscape(gen, (char) code);
                        }
                    }
                }
                gen.writeRaw('"');
            }
        });
        MAPPER.registerModule(simpleModule);
    }

    private JsonUtil() {
    }

    @SuppressWarnings("resource")
    public static String[] decode(final TextWebSocketFrame frame) throws IOException {
        final ByteBuf content = frame.content();
        if (content.readableBytes() == 0) {
            return EMPTY_STRING_ARRAY;
        }
        final ByteBufInputStream byteBufInputStream = new ByteBufInputStream(content);
        final byte firstByte = content.getByte(0);
        if (firstByte == '[') {
            return MAPPER.readValue(byteBufInputStream, String[].class);
        } else if (firstByte == '{') {
            return new String[] { content.toString(CharsetUtil.UTF_8) };
        } else {
            return new String[] { MAPPER.readValue(byteBufInputStream, String.class) };
        }
    }

    public static String[] decode(final String content) throws IOException {
        final JsonNode root = MAPPER.readTree(content);
        if (root.isObject()) {
            return new String[] { root.toString() };
        }

        if (root.isValueNode()) {
            return new String[] { root.asText() };
        }

        if (!root.isArray()) {
            throw new JsonMappingException("content must be a JSON Array but was : " + content);
        }
        final List<String> messages = new ArrayList<String>();
        final Iterator<JsonNode> elements = root.elements();
        while (elements.hasNext()) {
            final JsonNode field = elements.next();
            if (field.isValueNode()) {
                messages.add(field.asText());
            } else {
                messages.add(field.toString());
            }
        }
        return messages.toArray(new String[messages.size()]);
    }

    public static String encode(final String content) throws JsonMappingException {
        try {
            return MAPPER.writeValueAsString(content);
        } catch (Exception ignored) {
            throw new JsonMappingException("content must be a JSON Array but was : " + content);
        }
    }

}
