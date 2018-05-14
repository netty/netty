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
package io.netty.handler.codec.http2;

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
import io.netty.util.internal.StringUtil;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_HEADER_LIST_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.MAX_HEADER_LIST_SIZE;
import static io.netty.handler.codec.http2.Http2TestUtil.newTestEncoder;

final class HpackTestCase {

    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .registerTypeAdapter(HpackHeaderField.class, new HeaderFieldDeserializer())
            .create();

    int maxHeaderTableSize = -1;
    boolean sensitiveHeaders;

    List<HeaderBlock> headerBlocks;

    private HpackTestCase() {
    }

    static HpackTestCase load(InputStream is) {
        InputStreamReader r = new InputStreamReader(is);
        HpackTestCase hpackTestCase = GSON.fromJson(r, HpackTestCase.class);
        for (HeaderBlock headerBlock : hpackTestCase.headerBlocks) {
            headerBlock.encodedBytes = StringUtil.decodeHexDump(headerBlock.getEncodedStr());
        }
        return hpackTestCase;
    }

    void testCompress() throws Exception {
        HpackEncoder hpackEncoder = createEncoder();

        for (HeaderBlock headerBlock : headerBlocks) {

            byte[] actual =
                    encode(hpackEncoder, headerBlock.getHeaders(), headerBlock.getMaxHeaderTableSize(),
                            sensitiveHeaders);

            if (!Arrays.equals(actual, headerBlock.encodedBytes)) {
                throw new AssertionError(
                        "\nEXPECTED:\n" + headerBlock.getEncodedStr() +
                                "\nACTUAL:\n" + StringUtil.toHexString(actual));
            }

            List<HpackHeaderField> actualDynamicTable = new ArrayList<HpackHeaderField>();
            for (int index = 0; index < hpackEncoder.length(); index++) {
                actualDynamicTable.add(hpackEncoder.getHeaderField(index));
            }

            List<HpackHeaderField> expectedDynamicTable = headerBlock.getDynamicTable();

            if (!expectedDynamicTable.equals(actualDynamicTable)) {
                throw new AssertionError(
                        "\nEXPECTED DYNAMIC TABLE:\n" + expectedDynamicTable +
                                "\nACTUAL DYNAMIC TABLE:\n" + actualDynamicTable);
            }

            if (headerBlock.getTableSize() != hpackEncoder.size()) {
                throw new AssertionError(
                        "\nEXPECTED TABLE SIZE: " + headerBlock.getTableSize() +
                                "\n ACTUAL TABLE SIZE : " + hpackEncoder.size());
            }
        }
    }

    void testDecompress() throws Exception {
        HpackDecoder hpackDecoder = createDecoder();

        for (HeaderBlock headerBlock : headerBlocks) {

            List<HpackHeaderField> actualHeaders = decode(hpackDecoder, headerBlock.encodedBytes);

            List<HpackHeaderField> expectedHeaders = new ArrayList<HpackHeaderField>();
            for (HpackHeaderField h : headerBlock.getHeaders()) {
                expectedHeaders.add(new HpackHeaderField(h.name, h.value));
            }

            if (!expectedHeaders.equals(actualHeaders)) {
                throw new AssertionError(
                        "\nEXPECTED:\n" + expectedHeaders +
                                "\nACTUAL:\n" + actualHeaders);
            }

            List<HpackHeaderField> actualDynamicTable = new ArrayList<HpackHeaderField>();
            for (int index = 0; index < hpackDecoder.length(); index++) {
                actualDynamicTable.add(hpackDecoder.getHeaderField(index));
            }

            List<HpackHeaderField> expectedDynamicTable = headerBlock.getDynamicTable();

            if (!expectedDynamicTable.equals(actualDynamicTable)) {
                throw new AssertionError(
                        "\nEXPECTED DYNAMIC TABLE:\n" + expectedDynamicTable +
                                "\nACTUAL DYNAMIC TABLE:\n" + actualDynamicTable);
            }

            if (headerBlock.getTableSize() != hpackDecoder.size()) {
                throw new AssertionError(
                        "\nEXPECTED TABLE SIZE: " + headerBlock.getTableSize() +
                                "\n ACTUAL TABLE SIZE : " + hpackDecoder.size());
            }
        }
    }

    private HpackEncoder createEncoder() {
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

    private HpackDecoder createDecoder() {
        int maxHeaderTableSize = this.maxHeaderTableSize;
        if (maxHeaderTableSize == -1) {
            maxHeaderTableSize = Integer.MAX_VALUE;
        }

        return new HpackDecoder(DEFAULT_HEADER_LIST_SIZE, 32, maxHeaderTableSize);
    }

    private static byte[] encode(HpackEncoder hpackEncoder, List<HpackHeaderField> headers, int maxHeaderTableSize,
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
                hpackEncoder.setMaxHeaderTableSize(buffer, maxHeaderTableSize);
            }

            hpackEncoder.encodeHeaders(3 /* randomly chosen */, buffer, http2Headers, sensitivityDetector);
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            return bytes;
        } finally {
            buffer.release();
        }
    }

    private static Http2Headers toHttp2Headers(List<HpackHeaderField> inHeaders) {
        Http2Headers headers = new DefaultHttp2Headers(false);
        for (HpackHeaderField e : inHeaders) {
            headers.add(e.name, e.value);
        }
        return headers;
    }

    private static List<HpackHeaderField> decode(HpackDecoder hpackDecoder, byte[] expected) throws Exception {
        ByteBuf in = Unpooled.wrappedBuffer(expected);
        try {
            List<HpackHeaderField> headers = new ArrayList<HpackHeaderField>();
            TestHeaderListener listener = new TestHeaderListener(headers);
            hpackDecoder.decode(0, in, listener, true);
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
        private List<HpackHeaderField> headers;
        private List<HpackHeaderField> dynamicTable;
        private int tableSize;

        private int getMaxHeaderTableSize() {
            return maxHeaderTableSize;
        }

        public String getEncodedStr() {
            return concat(encoded).replaceAll(" ", "");
        }

        public List<HpackHeaderField> getHeaders() {
            return headers;
        }

        public List<HpackHeaderField> getDynamicTable() {
            return dynamicTable;
        }

        public int getTableSize() {
            return tableSize;
        }
    }

    static class HeaderFieldDeserializer implements JsonDeserializer<HpackHeaderField> {

        @Override
        public HpackHeaderField deserialize(JsonElement json, Type typeOfT,
                                            JsonDeserializationContext context) {
            JsonObject jsonObject = json.getAsJsonObject();
            Set<Map.Entry<String, JsonElement>> entrySet = jsonObject.entrySet();
            if (entrySet.size() != 1) {
                throw new JsonParseException("JSON Object has multiple entries: " + entrySet);
            }
            Map.Entry<String, JsonElement> entry = entrySet.iterator().next();
            String name = entry.getKey();
            String value = entry.getValue().getAsString();
            return new HpackHeaderField(name, value);
        }
    }
}
