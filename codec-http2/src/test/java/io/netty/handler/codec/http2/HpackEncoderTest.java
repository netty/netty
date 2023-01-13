/*
 * Copyright 2017 The Netty Project
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
package io.netty.handler.codec.http2;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.Random;

import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_HEADER_LIST_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.MAX_HEADER_TABLE_SIZE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

public class HpackEncoderTest {
    private HpackDecoder hpackDecoder;
    private HpackEncoder hpackEncoder;
    private Http2Headers mockHeaders;
    private ByteBuf buf;

    @BeforeEach
    public void setUp() {
        hpackEncoder = new HpackEncoder();
        hpackDecoder = new HpackDecoder(DEFAULT_HEADER_LIST_SIZE);
        mockHeaders = mock(Http2Headers.class);
        buf = Unpooled.buffer();
    }

    @AfterEach
    public void teardown() {
        buf.release();
    }

    @Test
    public void testSetMaxHeaderTableSizeToMaxValue() throws Http2Exception {
        hpackEncoder.setMaxHeaderTableSize(buf, MAX_HEADER_TABLE_SIZE);
        hpackDecoder.setMaxHeaderTableSize(MAX_HEADER_TABLE_SIZE);
        hpackDecoder.decode(0, buf, mockHeaders, true);
        assertEquals(MAX_HEADER_TABLE_SIZE, hpackDecoder.getMaxHeaderTableSize());
    }

    @Test
    public void testSetMaxHeaderTableSizeOverflow() {
        assertThrows(Http2Exception.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                hpackEncoder.setMaxHeaderTableSize(buf, MAX_HEADER_TABLE_SIZE + 1);
            }
        });
    }

    /**
     * The encoder should not impose an arbitrary limit on the header size if
     * the server has not specified any limit.
     * @throws Http2Exception
     */
    @Test
    public void testWillEncode16MBHeaderByDefault() throws Http2Exception {
        String bigHeaderName = "x-big-header";
        int bigHeaderSize = 1024 * 1024 * 16;
        String bigHeaderVal = new String(new char[bigHeaderSize]).replace('\0', 'X');
        Http2Headers headersIn = new DefaultHttp2Headers().add(
                "x-big-header", bigHeaderVal);
        Http2Headers headersOut = new DefaultHttp2Headers();

        hpackEncoder.encodeHeaders(0, buf, headersIn, Http2HeadersEncoder.NEVER_SENSITIVE);
        hpackDecoder.setMaxHeaderListSize(bigHeaderSize + 1024);
        hpackDecoder.decode(0, buf, headersOut, false);
        assertEquals(headersOut.get(bigHeaderName).toString(), bigHeaderVal);
    }

    @Test
    public void testSetMaxHeaderListSizeEnforcedAfterSet() throws Http2Exception {
        final Http2Headers headers = new DefaultHttp2Headers().add(
                "x-big-header",
                new String(new char[1024 * 16]).replace('\0', 'X')
        );

        hpackEncoder.setMaxHeaderListSize(1000);

        assertThrows(Http2Exception.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                hpackEncoder.encodeHeaders(0, buf, headers, Http2HeadersEncoder.NEVER_SENSITIVE);
            }
        });
    }

    @Test
    public void testEncodeUsingBothStaticAndDynamicTable() throws Http2Exception {
        final Http2Headers headers = new DefaultHttp2Headers()
          // :method -> POST is found in the static table.
          .add(":method", "POST")

          // ":path" is found in the static table but only matches "/" and "/index.html".
          .add(":path", "/dev/null")

          // "accept-language" is found in the static table, but with no matching value.
          .add("accept-language", "fr")

          // k -> x is not in the static table.
          .add("k", "x");

        // :method -> POST gets encoded by reference.
        // :path -> /dev/null
        //      :path gets encoded by reference, /dev/null literally.
        // accept-language -> fr
        //      accept-language gets encoded by reference, fr literally.
        // k -> x
        //      both k and x get encoded literally.
        verifyEncoding(headers,
          -125, 68, 9, 47, 100, 101, 118, 47, 110, 117, 108, 108, 81, 2, 102, 114, 64, 1, 107, 1, 120);

        // encoded using references to previous headers.
        verifyEncoding(headers, -125, -64, -65, -66);
    }

    @Test
    public void testSameHeaderNameMultipleValues() throws Http2Exception {
        final Http2Headers headers = new DefaultHttp2Headers()
          .add("k", "x")
          .add("k", "y");

        // k -> x encoded literally, k -> y encoded by referencing k of the k -> x header,
        // y gets encoded literally.
        verifyEncoding(headers, 64, 1, 107, 1, 120, 126, 1, 121);

        // both k -> x and k -> y encoded by reference.
        verifyEncoding(headers, -65, -66);
    }

    @Test
    public void testEviction() throws Http2Exception {
        setMaxTableSize(2 * HpackHeaderField.HEADER_ENTRY_OVERHEAD + 3);

        // k -> x encoded literally
        verifyEncoding(new DefaultHttp2Headers().add("k", "x"), 63, 36, 64, 1, 107, 1, 120);

        // k -> x encoded by referencing the previously encoded k -> x.
        verifyEncoding(new DefaultHttp2Headers().add("k", "x"), -66);

        // k -> x gets evicted
        verifyEncoding(new DefaultHttp2Headers().add("k", "y"), 64, 1, 107, 1, 121);

        // k -> x was evicted, so we are back to literal encoding.
        verifyEncoding(new DefaultHttp2Headers().add("k", "x"), 64, 1, 107, 1, 120);
    }

    @Test
    public void testTableResize() throws Http2Exception {
        verifyEncoding(new DefaultHttp2Headers().add("k", "x").add("k", "y"), 64, 1, 107, 1, 120, 126, 1, 121);

        // k -> x gets encoded by referencing the previously encoded k -> x.
        verifyEncoding(new DefaultHttp2Headers().add("k", "x"), -65);

        // k -> x gets evicted
        setMaxTableSize(2 * HpackHeaderField.HEADER_ENTRY_OVERHEAD + 3);

        // k -> x header was evicted, so we are back to literal encoding.
        verifyEncoding(new DefaultHttp2Headers().add("k", "x"), 63, 36, 64, 1, 107, 1, 120);

        // make room for k -> y
        setMaxTableSize(1000);

        verifyEncoding(new DefaultHttp2Headers().add("k", "y"), 63, -55, 7, 126, 1, 121);

        // both k -> x and k -> y are encoded by reference.
        verifyEncoding(new DefaultHttp2Headers().add("k", "x").add("k", "y"), -65, -66);
    }

    @Test
    public void testManyHeaderCombinations() throws Http2Exception {
        final Random r = new Random(0);
        for (int i = 0; i < 50000; i++) {
            if (r.nextInt(10) == 0) {
                setMaxTableSize(r.nextBoolean() ? 0 : r.nextInt(4096));
            }
            verifyRoundTrip(new DefaultHttp2Headers()
              .add("k" + r.nextInt(20), "x" + r.nextInt(500))
              .add(":method", r.nextBoolean() ? "GET" : "POST")
              .add(":path", "/dev/null")
              .add("accept-language", String.valueOf(r.nextBoolean()))
            );
            buf.clear();
        }
    }

    private void setMaxTableSize(int maxHeaderTableSize) throws Http2Exception {
        hpackEncoder.setMaxHeaderTableSize(buf, maxHeaderTableSize);
        hpackDecoder.setMaxHeaderTableSize(maxHeaderTableSize);
    }

    private void verifyEncoding(Http2Headers encodedHeaders, int... encoding) throws Http2Exception {
        verifyRoundTrip(encodedHeaders);
        verifyEncodedBytes(encoding);
        buf.clear();
    }

    private void verifyRoundTrip(Http2Headers encodedHeaders) throws Http2Exception {
        hpackEncoder.encodeHeaders(0, buf, encodedHeaders, Http2HeadersEncoder.NEVER_SENSITIVE);
        DefaultHttp2Headers decodedHeaders = new DefaultHttp2Headers();
        hpackDecoder.decode(0, buf, decodedHeaders, true);
        assertEquals(encodedHeaders, decodedHeaders);
    }

    private void verifyEncodedBytes(int... expectedEncoding) {
        // We want to copy everything that was written to the buffer.
        byte[] actualEncoding = new byte[buf.writerIndex()];
        buf.getBytes(0, actualEncoding);
        Assertions.assertArrayEquals(toByteArray(expectedEncoding), actualEncoding);
    }

    private byte[] toByteArray(int[] encoding) {
        byte[] expectedEncoding = new byte[encoding.length];
        for (int i = 0; i < encoding.length; i++) {
            expectedEncoding[i] = (byte) encoding[i];
        }
        return expectedEncoding;
    }
}
