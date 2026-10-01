/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.handler.codec.http2;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http2.Http2Exception.StreamException;
import io.netty.util.AsciiString;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static io.netty.handler.codec.http2.Http2TestUtil.newTestEncoder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link DefaultHttp2HeadersEncoder}.
 */
public class DefaultHttp2HeadersEncoderTest {

    private DefaultHttp2HeadersEncoder encoder;

    @BeforeEach
    public void setup() {
        encoder = new DefaultHttp2HeadersEncoder(Http2HeadersEncoder.NEVER_SENSITIVE, newTestEncoder());
    }

    @AfterEach
    public void tearDown() {
        encoder.close();
    }

    @Test
    public void encodeShouldSucceed() throws Http2Exception {
        Http2Headers headers = headers();
        ByteBuf buf = Unpooled.buffer();
        try {
            encoder.encodeHeaders(3 /* randomly chosen */, headers, buf);
            assertTrue(buf.writerIndex() > 0);
        } finally {
            buf.release();
        }
    }

    @Test
    public void headersExceedMaxSetSizeShouldFail() throws Http2Exception {
        final Http2Headers headers = headers();
        encoder.maxHeaderListSize(2);
        assertThrows(StreamException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                ByteBuf buf = Unpooled.buffer();
                try {
                    encoder.encodeHeaders(3 /* randomly chosen */, headers, buf);
                } finally {
                    buf.release();
                }
            }
        });
    }

    @ParameterizedTest(name = "{displayName} [{index}] sizes={0} updates={1}")
    @CsvSource(delimiter = '|', value = {
            "2048 1024      | 1024",
            "100 0          | 0",
            "4096 2048 1024 | 1024",
            "1024 2048      | 1024 2048",
            "2048 1024 2048 | 1024 2048",
            "1024 4096      | 1024 4096",
            "1024           | 1024",
            "4096           | ''",
    })
    public void tableSizeChangesBetweenHeaderBlocksSignalSmallestAndFinalSize(String sizes, String updates)
            throws Http2Exception {
        // The peer acknowledges every SETTINGS_HEADER_TABLE_SIZE before our next header block arrives, so its
        // decoder only accepts table size updates up to the last size (RFC 7541, Section 4.2 and 6.3).
        DefaultHttp2HeadersEncoder encoder = new DefaultHttp2HeadersEncoder();
        HpackDecoder decoder = new HpackDecoder(Http2CodecUtil.DEFAULT_HEADER_LIST_SIZE);
        ByteBuf buf = Unpooled.buffer();
        try {
            for (String size : sizes.trim().split(" +")) {
                decoder.setMaxHeaderTableSize(Long.parseLong(size));
                encoder.maxHeaderTableSize(Long.parseLong(size));
            }
            Http2Headers headers = headers();
            encoder.encodeHeaders(3, headers, buf);
            assertThat(tableSizeUpdates(buf)).containsExactlyElementsOf(longs(updates));

            Http2Headers decoded = new DefaultHttp2Headers();
            decoder.decode(3, buf, decoded, true);
            assertThat(decoded).isEqualTo(headers);
        } finally {
            buf.release();
            encoder.close();
        }
    }

    @Test
    public void tableSizeUpdateIsSentWithNextHeaderBlockIfEncodingFails() throws Http2Exception {
        DefaultHttp2HeadersEncoder encoder = new DefaultHttp2HeadersEncoder();
        HpackDecoder decoder = new HpackDecoder(Http2CodecUtil.DEFAULT_HEADER_LIST_SIZE);
        decoder.setMaxHeaderTableSize(1024);
        encoder.maxHeaderTableSize(1024);
        encoder.maxHeaderListSize(200);
        final Http2Headers tooLarge = new DefaultHttp2Headers().add("a", new String(new char[300]).replace('\0', 'x'));
        ByteBuf buf = Unpooled.buffer();
        try {
            assertThrows(StreamException.class, () -> encoder.encodeHeaders(3, tooLarge, buf));
            buf.clear();

            Http2Headers headers = headers();
            encoder.encodeHeaders(5, headers, buf);
            assertThat(tableSizeUpdates(buf)).containsExactly(1024L);

            Http2Headers decoded = new DefaultHttp2Headers();
            decoder.decode(5, buf, decoded, true);
            assertThat(decoded).isEqualTo(headers);
        } finally {
            buf.release();
            encoder.close();
        }
    }

    private static List<Long> longs(String values) {
        List<Long> list = new ArrayList<>();
        for (String value : values.trim().split(" +")) {
            if (!value.isEmpty()) {
                list.add(Long.parseLong(value));
            }
        }
        return list;
    }

    /**
     * Reads the Dynamic Table Size Updates (RFC 7541, Section 6.3) at the start of a header block.
     */
    private static List<Long> tableSizeUpdates(ByteBuf block) {
        List<Long> updates = new ArrayList<>();
        int i = block.readerIndex();
        while (i < block.writerIndex() && (block.getByte(i) & 0xE0) == 0x20) {
            long value = block.getByte(i++) & 0x1F;
            if (value == 0x1F) {
                int shift = 0;
                byte b;
                do {
                    b = block.getByte(i++);
                    value += (long) (b & 0x7F) << shift;
                    shift += 7;
                } while ((b & 0x80) != 0);
            }
            updates.add(value);
        }
        return updates;
    }

    private static Http2Headers headers() {
        return new DefaultHttp2Headers().method(new AsciiString("GET")).add(new AsciiString("a"), new AsciiString("1"))
                .add(new AsciiString("a"), new AsciiString("2"));
    }
}
