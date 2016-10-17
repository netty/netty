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
 * Copyright 2015 Twitter, Inc.
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
package io.netty.microbench.http2.internal.hpack;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2HeadersEncoder;
import io.netty.handler.codec.http2.internal.hpack.Decoder;
import io.netty.handler.codec.http2.internal.hpack.Encoder;
import io.netty.microbench.util.AbstractMicrobenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;

import static io.netty.handler.codec.http2.Http2CodecUtil.MAX_HEADER_LIST_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.MAX_HEADER_TABLE_SIZE;
import static io.netty.microbench.http2.internal.hpack.HpackUtilBenchmark.newTestEncoder;

public class DecoderBenchmark extends AbstractMicrobenchmark {

    @Param
    public HeadersSize size;

    @Param({ "true", "false" })
    public boolean sensitive;

    @Param({ "true", "false" })
    public boolean limitToAscii;

    private ByteBuf input;

    @Setup(Level.Trial)
    public void setup() throws Http2Exception {
        input = Unpooled.wrappedBuffer(getSerializedHeaders(Util.http2Headers(size, limitToAscii), sensitive));
    }

    @TearDown(Level.Trial)
    public void teardown() {
        input.release();
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void decode(final Blackhole bh) throws Http2Exception {
        Decoder decoder = new Decoder();
        @SuppressWarnings("unchecked")
        Http2Headers headers =
                new DefaultHttp2Headers() {
            @Override
            public Http2Headers add(CharSequence name, CharSequence value) {
                bh.consume(sensitive);
                return this;
            }
        };
        decoder.decode(0, input.duplicate(), headers);
    }

    private byte[] getSerializedHeaders(Http2Headers headers, boolean sensitive) throws Http2Exception {
        Encoder encoder = newTestEncoder();
        ByteBuf out = size.newOutBuffer();
        try {
            encoder.encodeHeaders(3 /* randomly chosen */, out, headers,
                                  sensitive ? Http2HeadersEncoder.ALWAYS_SENSITIVE
                                            : Http2HeadersEncoder.NEVER_SENSITIVE);
            byte[] bytes = new byte[out.readableBytes()];
            out.readBytes(bytes);
            return bytes;
        } finally {
            out.release();
        }
    }
}
