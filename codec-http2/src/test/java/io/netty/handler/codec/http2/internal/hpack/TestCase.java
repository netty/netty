/*
 * Copyright 2015 The Netty Project
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

/*
 * Copyright 2014 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.netty.handler.codec.http2.internal.hpack;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2HeadersEncoder;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.netty.handler.codec.http2.Http2CodecUtil.MAX_HEADER_LIST_SIZE;
import static io.netty.handler.codec.http2.Http2TestUtil.newTestEncoder;

final class TestCase {

    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .registerTypeAdapter(HeaderField.class, new HeaderFieldDeserializer())
            .create();

    int maxHeaderTableSize = -1;
    boolean sensitiveHeaders;

    List<HeaderBlock> headerBlocks;

    private TestCase() {
    }

    static TestCase load(InputStream is) throws IOException {
        InputStreamReader r = new InputStreamReader(is);
        TestCase testCase = GSON.fromJson(r, TestCase.class);
        for (HeaderBlock headerBlock : testCase.headerBlocks) {
            headerBlock.encodedBytes = Hex.decodeHex(headerBlock.getEncodedStr().toCharArray());
        }
        return testCase;
    }

    void testCompress() throws Exception {
        Encoder encoder = createEncoder();

        for (HeaderBlock headerBlock : headerBlocks) {

            byte[] actual =
                    encode(encoder, headerBlock.getHeaders(), headerBlock.getMaxHeaderTableSize(),
                            sensitiveHeaders);

            if (!Arrays.equals(actual, headerBlock.encodedBytes)) {
                throw new AssertionError(
                        "\nEXPECTED:\n" + headerBlock.getEncodedStr() +
                                "\nACTUAL:\n" + Hex.encodeHexString(actual));
            }

            List<HeaderField> actualDynamicTable = new ArrayList<HeaderField>();
            for (int index = 0; index < encoder.length(); index++) {
                actualDynamicTable.add(encoder.getHeaderField(index));
            }

            List<HeaderField> expectedDynamicTable = headerBlock.getDynamicTable();

            if (!expectedDynamicTable.equals(actualDynamicTable)) {
                throw new AssertionError(
                        "\nEXPECTED DYNAMIC TABLE:\n" + expectedDynamicTable +
                                "\nACTUAL DYNAMIC TABLE:\n" + actualDynamicTable);
            }

            if (headerBlock.getTableSize() != encoder.size()) {
                throw new AssertionError(
                        "\nEXPECTED TABLE SIZE: " + headerBlock.getTableSize() +
                                "\n ACTUAL TABLE SIZE : " + encoder.size());
            }
        }
    }

    void testDecompress() throws Exception {
        Decoder decoder = createDecoder();

        for (HeaderBlock headerBlock : headerBlocks) {

            List<HeaderField> actualHeaders = decode(decoder, headerBlock.encodedBytes);

            List<HeaderField> expectedHeaders = new ArrayList<HeaderField>();
            for (HeaderField h : headerBlock.getHeaders()) {
                expectedHeaders.add(new HeaderField(h.name, h.value));
            }

            if (!expectedHeaders.equals(actualHeaders)) {
                throw new AssertionError(
                        "\nEXPECTED:\n" + expectedHeaders +
                                "\nACTUAL:\n" + actualHeaders);
            }

            List<HeaderField> actualDynamicTable = new ArrayList<HeaderField>();
            for (int index = 0; index < decoder.length(); index++) {
                actualDynamicTable.add(decoder.getHeaderField(index));
            }

            List<HeaderField> expectedDynamicTable = headerBlock.getDynamicTable();

            if (!expectedDynamicTable.equals(actualDynamicTable)) {
                throw new AssertionError(
                        "\nEXPECTED DYNAMIC TABLE:\n" + expectedDynamicTable +
                                "\nACTUAL DYNAMIC TABLE:\n" + actualDynamicTable);
            }

            if (headerBlock.getTableSize() != decoder.size()) {
                throw new AssertionError(
                        "\nEXPECTED TABLE SIZE: " + headerBlock.getTableSize() +
                                "\n ACTUAL TABLE SIZE : " + decoder.size());
            }
        }
    }

    private Encoder createEncoder() {
        int maxHeaderTableSize = this.maxHeaderTableSize;
        if (maxHeaderTableSize == -1) {
            maxHeaderTableSize = Integer.MAX_VALUE;
        }

        try {
            return newTestEncoder(true, MAX_HEADER_LIST_SIZE, maxHeaderTableSize);
        } catch (Http2Exception e) {
            throw new Error("invalid initial values!", e);
        }
    }

    private Decoder createDecoder() {
        int maxHeaderTableSize = this.maxHeaderTableSize;
        if (maxHeaderTableSize == -1) {
            maxHeaderTableSize = Integer.MAX_VALUE;
        }

        return new Decoder(32, maxHeaderTableSize);
    }

    private static byte[] encode(Encoder encoder, List<HeaderField> headers, int maxHeaderTableSize,
                                 final boolean sensitive) throws Http2Exception {
        Http2Headers http2Headers = toHttp2Headers(headers);
        Http2HeadersEncoder.SensitivityDetector sensitivityDetector = new Http2HeadersEncoder.SensitivityDetector() {
            @Override
            public boolean isSensitive(CharSequence name, CharSequence value) {
                return sensitive;
            }
        };
        ByteBuf buffer = Unpooled.buffer();
        try {
            if (maxHeaderTableSize != -1) {
                encoder.setMaxHeaderTableSize(buffer, maxHeaderTableSize);
            }

            encoder.encodeHeaders(3 /* randomly chosen */, buffer, http2Headers, sensitivityDetector);
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            return bytes;
        } finally {
            buffer.release();
        }
    }

    private static Http2Headers toHttp2Headers(List<HeaderField> inHeaders) {
        Http2Headers headers = new DefaultHttp2Headers(false);
        for (HeaderField e : inHeaders) {
            headers.add(e.name, e.value);
        }
        return headers;
    }

    private static List<HeaderField> decode(Decoder decoder, byte[] expected) throws Exception {
        ByteBuf in = Unpooled.wrappedBuffer(expected);
        try {
            List<HeaderField> headers = new ArrayList<HeaderField>();
            TestHeaderListener listener = new TestHeaderListener(headers);
            decoder.decode(0, in, listener);
            return headers;
        } finally {
            in.release();
        }
    }

    private static String concat(List<String> l) {
        StringBuilder ret = new StringBuilder();
        for (String s : l) {
            ret.append(s);
        }
        return ret.toString();
    }

    static class HeaderBlock {
        private int maxHeaderTableSize = -1;
        private byte[] encodedBytes;
        private List<String> encoded;
        private List<HeaderField> headers;
        private List<HeaderField> dynamicTable;
        private int tableSize;

        private int getMaxHeaderTableSize() {
            return maxHeaderTableSize;
        }

        public String getEncodedStr() {
            return concat(encoded).replaceAll(" ", "");
        }

        public List<HeaderField> getHeaders() {
            return headers;
        }

        public List<HeaderField> getDynamicTable() {
            return dynamicTable;
        }

        public int getTableSize() {
            return tableSize;
        }
    }

    static class HeaderFieldDeserializer implements JsonDeserializer<HeaderField> {

        @Override
        public HeaderField deserialize(JsonElement json, Type typeOfT,
                                       JsonDeserializationContext context) {
            JsonObject jsonObject = json.getAsJsonObject();
            Set<Map.Entry<String, JsonElement>> entrySet = jsonObject.entrySet();
            if (entrySet.size() != 1) {
                throw new JsonParseException("JSON Object has multiple entries: " + entrySet);
            }
            Map.Entry<String, JsonElement> entry = entrySet.iterator().next();
            String name = entry.getKey();
            String value = entry.getValue().getAsString();
            return new HeaderField(name, value);
        }
    }
}
