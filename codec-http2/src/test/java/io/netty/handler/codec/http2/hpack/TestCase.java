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
package io.netty.handler.codec.http2.hpack;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class TestCase {

    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .registerTypeAdapter(HeaderField.class, new HeaderFieldDeserializer())
            .create();

    int maxHeaderTableSize = -1;
    boolean useIndexing = true;
    boolean sensitiveHeaders;
    boolean forceHuffmanOn;
    boolean forceHuffmanOff;

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

        return new Encoder(maxHeaderTableSize, useIndexing, forceHuffmanOn, forceHuffmanOff);
    }

    private Decoder createDecoder() {
        int maxHeaderTableSize = this.maxHeaderTableSize;
        if (maxHeaderTableSize == -1) {
            maxHeaderTableSize = Integer.MAX_VALUE;
        }

        return new Decoder(8192, maxHeaderTableSize);
    }

    private static byte[] encode(Encoder encoder, List<HeaderField> headers, int maxHeaderTableSize,
                                 boolean sensitive)
            throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        if (maxHeaderTableSize != -1) {
            encoder.setMaxHeaderTableSize(baos, maxHeaderTableSize);
        }

        for (HeaderField e : headers) {
            encoder.encodeHeader(baos, e.name, e.value, sensitive);
        }

        return baos.toByteArray();
    }

    private static List<HeaderField> decode(Decoder decoder, byte[] expected) throws IOException {
        List<HeaderField> headers = new ArrayList<HeaderField>();
        TestHeaderListener listener = new TestHeaderListener(headers);
        decoder.decode(new ByteArrayInputStream(expected), listener);
        decoder.endHeaderBlock();
        return headers;
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
                                       JsonDeserializationContext context)
                throws JsonParseException {
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
