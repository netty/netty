/*
 * Copyright 2016 The Netty Project
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
import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.AsciiString;
import io.netty.util.internal.ConstantTimeUtils;
import io.netty.util.internal.PlatformDependent;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.util.List;

import static io.netty.handler.codec.http2.Http2CodecUtil.MAX_HEADER_LIST_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.MAX_HEADER_TABLE_SIZE;

@Threads(1)
@Warmup(iterations = 5)
@Measurement(iterations = 5)
public class HpackUtilBenchmark extends AbstractMicrobenchmark {
    @Param
    public HpackHeadersSize size;

    private List<HpackHeader> hpackHeaders;

    @Setup(Level.Trial)
    public void setup() {
        hpackHeaders = HpackBenchmarkUtil.headers(size, false);
    }

    @Benchmark
    public int oldEquals() {
        int count = 0;
        for (int i = 0; i < hpackHeaders.size(); ++i) {
            HpackHeader hpackHeader = hpackHeaders.get(i);
            if (oldEquals(hpackHeader.name, hpackHeader.name)) {
                ++count;
            }
        }
        return count;
    }

    @Benchmark
    public int newEquals() {
        int count = 0;
        for (int i = 0; i < hpackHeaders.size(); ++i) {
            HpackHeader hpackHeader = hpackHeaders.get(i);
            if (newEquals(hpackHeader.name, hpackHeader.name)) {
                ++count;
            }
        }
        return count;
    }

    private static boolean oldEquals(CharSequence s1, CharSequence s2) {
        if (s1.length() != s2.length()) {
            return false;
        }
        char c = 0;
        for (int i = 0; i < s1.length(); i++) {
            c |= s1.charAt(i) ^ s2.charAt(i);
        }
        return c == 0;
    }

    private static boolean newEquals(CharSequence s1, CharSequence s2) {
        if (s1 instanceof AsciiString && s2 instanceof AsciiString) {
            if (s1.length() != s2.length()) {
                return false;
            }
            AsciiString s1Ascii = (AsciiString) s1;
            AsciiString s2Ascii = (AsciiString) s2;
            return PlatformDependent.equalsConstantTime(s1Ascii.array(), s1Ascii.arrayOffset(),
                                                        s2Ascii.array(), s2Ascii.arrayOffset(), s1.length()) != 0;
        }

        return ConstantTimeUtils.equalsConstantTime(s1, s2) != 0;
    }

    static HpackEncoder newTestEncoder() {
        HpackEncoder hpackEncoder = new HpackEncoder();
        ByteBuf buf = Unpooled.buffer();
        try {
            hpackEncoder.setMaxHeaderTableSize(buf, MAX_HEADER_TABLE_SIZE);
            hpackEncoder.setMaxHeaderListSize(MAX_HEADER_LIST_SIZE);
        } catch (Http2Exception e) {
            throw new Error("max size not allowed?", e);
        } finally  {
            buf.release();
        }
        return hpackEncoder;
    }
}
