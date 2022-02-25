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
package io.netty5.microbench.http;

import io.netty5.buffer.PooledByteBufAllocator;
import io.netty5.buffer.UnpooledByteBufAllocator;
import io.netty5.buffer.api.Buffer;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.handler.codec.http.DefaultFullHttpRequest;
import io.netty5.handler.codec.http.DefaultHttpHeaders;
import io.netty5.handler.codec.http.DefaultHttpRequest;
import io.netty5.handler.codec.http.DefaultLastHttpContent;
import io.netty5.handler.codec.http.EmptyHttpHeaders;
import io.netty5.handler.codec.http.FullHttpRequest;
import io.netty5.handler.codec.http.HttpHeaderNames;
import io.netty5.handler.codec.http.HttpHeaderValues;
import io.netty5.handler.codec.http.HttpHeaders;
import io.netty5.handler.codec.http.HttpMethod;
import io.netty5.handler.codec.http.HttpRequest;
import io.netty5.handler.codec.http.HttpRequestEncoder;
import io.netty5.handler.codec.http.HttpVersion;
import io.netty5.handler.codec.http.LastHttpContent;
import io.netty5.microbench.channel.EmbeddedChannelWriteReleaseHandlerContext;
import io.netty5.microbench.util.AbstractMicrobenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import static io.netty5.buffer.api.DefaultBufferAllocators.preferredAllocator;

@State(Scope.Benchmark)
@Fork(1)
@Threads(1)
@Warmup(iterations = 5)
@Measurement(iterations = 10)
public class HttpObjectEncoderBenchmark extends AbstractMicrobenchmark {
    private HttpRequestEncoder encoder;
    private FullHttpRequest fullRequest;
    private LastHttpContent<?> lastContent;
    private HttpRequest contentLengthRequest;
    private HttpRequest chunkedRequest;
    private Buffer content;
    private ChannelHandlerContext context;

    @Param({ "true", "false" })
    public boolean pooledAllocator;

    @Setup(Level.Trial)
    public void setup() {
        byte[] bytes = new byte[256];
        content = preferredAllocator().allocate(bytes.length);
        content.writeBytes(bytes);
        Buffer testContent = content.copy().makeReadOnly();
        HttpHeaders headersWithChunked = new DefaultHttpHeaders(false);
        headersWithChunked.add(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        HttpHeaders headersWithContentLength = new DefaultHttpHeaders(false);
        headersWithContentLength.add(HttpHeaderNames.CONTENT_LENGTH, testContent.readableBytes());

        fullRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/index", testContent,
                headersWithContentLength, EmptyHttpHeaders.INSTANCE);
        contentLengthRequest = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/index",
                headersWithContentLength);
        chunkedRequest = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/index", headersWithChunked);
        lastContent = new DefaultLastHttpContent(testContent, false);

        encoder = new HttpRequestEncoder();
        context = new EmbeddedChannelWriteReleaseHandlerContext(pooledAllocator ? PooledByteBufAllocator.DEFAULT :
                UnpooledByteBufAllocator.DEFAULT, encoder) {
            @Override
            protected void handleException(Throwable t) {
                handleUnexpectedException(t);
            }
        };
    }

    @TearDown(Level.Trial)
    public void teardown() {
        content.close();
        content = null;
    }

    @Benchmark
    public void fullMessage() {
        encoder.write(context, fullRequest);
    }

    @Benchmark
    public void contentLength() {
        encoder.write(context, contentLengthRequest);
        encoder.write(context, lastContent);
    }

    @Benchmark
    public void chunked() {
        encoder.write(context, chunkedRequest);
        encoder.write(context, lastContent);
    }
}
