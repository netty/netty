/*
 * Copyright 2020 The Netty Project
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
package io.netty.incubator.codec.http3;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.AsciiString;

public class QpackEncoderDecoderTest {
    @Test
    public void testEncodeDecode() throws Http3Exception {
        final QpackEncoder encoder = new QpackEncoder();
        final QpackDecoder decoder = new QpackDecoder();

        final ByteBuf out = Unpooled.buffer();

        final Http3Headers encHeaders = new DefaultHttp3Headers();
        encHeaders.add(":authority", "netty.quic"); // name only
        encHeaders.add(":path", "/"); // name & value
        encHeaders.add(":method", "GET"); // name & value with few options per name
        encHeaders.add(":status", "417"); // name & multiple values but value is missing
        encHeaders.add("x-qpack-draft", "19");

        final Http3Headers decHeaders = new DefaultHttp3Headers();

        encoder.encodeHeaders(out, encHeaders);
        decoder.decodeHeaders(out, decHeaders, false);

        assertEquals(5, decHeaders.size());
        assertEquals(new AsciiString("netty.quic"), decHeaders.authority());
        assertEquals(new AsciiString("/"), decHeaders.path());
        assertEquals(new AsciiString("GET"), decHeaders.method());
        assertEquals(new AsciiString("417"), decHeaders.status());
        assertEquals(new AsciiString("19"), decHeaders.get("x-qpack-draft"));

        out.release();
    }
}
