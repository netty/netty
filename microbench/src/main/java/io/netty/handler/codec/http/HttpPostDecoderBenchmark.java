/*
 * Copyright 2024 The Netty Project
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
package io.netty.handler.codec.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.internal.PlatformDependent;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Threads(1)
@Warmup(iterations = 3)
@Measurement(iterations = 3)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
public class HttpPostDecoderBenchmark extends AbstractMicrobenchmark {
    @Param({ "false", "true" })
    public boolean direct;

    private ByteBuf buf;
    private HttpRequest request;

    @Setup()
    public void setUp() {
        request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/post");
        request.headers().add(HttpHeaderNames.CONTENT_TYPE, "application/x-www-form-urlencoded");
        buf = direct ? Unpooled.directBuffer() : Unpooled.buffer();
        for (int i = 0; i < 100; i++) {
            if (i != 0) {
                buf.writeByte('&');
            }
            ByteBufUtil.writeAscii(buf, "form-field-" + i);
            buf.writeByte('=');

            ByteBufUtil.writeAscii(buf, randomString());
        }
    }

    private static CharSequence randomString() {
        Random rng = PlatformDependent.threadLocalRandom();
        int len = 4 + rng.nextInt(110);
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            String chars = "_-0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJLKMNOPQRSTUVWXYZ";
            sb.append(chars.charAt(rng.nextInt(chars.length())));
        }
        return sb;
    }

    @Benchmark
    public List<InterfaceHttpData> decode() {
        HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(request);
        DefaultLastHttpContent content = new DefaultLastHttpContent(buf.duplicate());
        decoder.offer(content);
        return decoder.getBodyHttpDatas();
    }
}
