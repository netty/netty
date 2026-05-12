/*
 * Copyright 2015 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
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
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.netty.handler.codec.http2;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.internal.StringUtil;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_HEADER_LIST_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.MAX_HEADER_LIST_SIZE;
import static io.netty.handler.codec.http2.Http2TestUtil.newTestEncoder;

@JsonIgnoreProperties(ignoreUnknown = true)
final class HpackTestCase {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
            .setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE)
            .setVisibility(PropertyAccessor.SETTER, JsonAutoDetect.Visibility.NONE)
            .setVisibility(PropertyAccessor.CREATOR, JsonAutoDetect.Visibility.ANY)
            .registerModule(new SimpleModule()
                    .addDeserializer(HpackHeaderField.class, new HeaderFieldDeserializer()));

    int maxHeaderTableSize = -1;
    boolean sensitiveHeaders;

    List<HeaderBlock> headerBlocks;

    private HpackTestCase() {
    }

    static HpackTestCase load(InputStream is) throws IOException {
        HpackTestCase hpackTestCase = MAPPER.readValue(is, HpackTestCase.class);
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

            if (!headersEqual(expectedDynamicTable, actualDynamicTable)) {
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

            if (!headersEqual(expectedHeaders, actualHeaders)) {
                throw new AssertionError(
                        "\nEXPECTED:\n" + expectedHeaders +
                                "\nACTUAL:\n" + actualHeaders);
            }

            List<HpackHeaderField> actualDynamicTable = new ArrayList<HpackHeaderField>();
            for (int index = 0; index < hpackDecoder.length(); index++) {
                actualDynamicTable.add(hpackDecoder.getHeaderField(index));
            }

            List<HpackHeaderField> expectedDynamicTable = headerBlock.getDynamicTable();

            if (!headersEqual(expectedDynamicTable, actualDynamicTable)) {
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

        return new HpackDecoder(DEFAULT_HEADER_LIST_SIZE, maxHeaderTableSize);
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

    private static boolean headersEqual(List<HpackHeaderField> expected, List<HpackHeaderField> actual) {
        if (expected.size() != actual.size()) {
            return false;
        }
        for (int i = 0; i < expected.size(); i++) {
            if (!expected.get(i).equalsForTest(actual.get(i))) {
                return false;
            }
        }
        return true;
    }

    static class HeaderBlock {
        @SuppressWarnings("FieldMayBeFinal")
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

    static class HeaderFieldDeserializer extends StdDeserializer<HpackHeaderField> {

        HeaderFieldDeserializer() {
            super(HpackHeaderField.class);
        }

        @Override
        public HpackHeaderField deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            Set<Map.Entry<String, JsonNode>> properties = node.properties();
            if (properties.size() != 1) {
                throw MismatchedInputException.from(p, HpackHeaderField.class,
                        "JSON Object must have exactly one entry, got: " + properties.size());
            }
            Map.Entry<String, JsonNode> entry = properties.iterator().next();
            String name = entry.getKey();
            String value = entry.getValue().asText();
            return new HpackHeaderField(name, value);
        }
    }
}
