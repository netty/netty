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
package io.netty.microbench.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.DefaultHttpHeadersFactory;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.microbench.channel.EmbeddedChannelWriteReleaseHandlerContext;
import io.netty.microbench.util.AbstractMicrobenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Thread)
@Fork(2)
@Warmup(iterations = 5)
@Measurement(iterations = 10)
public class HttpRequestEncoderBenchmark extends AbstractMicrobenchmark {
    private HttpRequestEncoder encoder;
    private FullHttpRequest fullRequest;
    private LastHttpContent lastContent;
    private HttpRequest contentLengthRequest;
    private HttpRequest chunkedRequest;
    private ByteBuf content;
    private ChannelHandlerContext context;

    @Param({ "true", "false" })
    public boolean pooledAllocator;

    @Param({ "true", "false" })
    public boolean voidPromise;

    @Param({ "false", "true" })
    public boolean typePollution;

    @Param({ "128" })
    private int contentBytes;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        byte[] bytes = new byte[contentBytes];
        content = Unpooled.buffer(bytes.length);
        content.writeBytes(bytes);
        ByteBuf testContent = Unpooled.unreleasableBuffer(content.asReadOnly());
        DefaultHttpHeadersFactory headersFactory = DefaultHttpHeadersFactory.headersFactory().withValidation(false);
        HttpHeaders headersWithChunked = headersFactory.newHeaders();
        headersWithChunked.add(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        HttpHeaders headersWithContentLength = headersFactory.newHeaders();
        headersWithContentLength.add(HttpHeaderNames.CONTENT_LENGTH, testContent.readableBytes());

        fullRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/index", testContent,
                headersWithContentLength, EmptyHttpHeaders.INSTANCE);
        contentLengthRequest = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/index",
                headersWithContentLength);
        chunkedRequest = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/index", headersWithChunked);
        lastContent = new DefaultLastHttpContent(testContent, headersFactory);

        encoder = new HttpRequestEncoder();
        context = new EmbeddedChannelWriteReleaseHandlerContext(pooledAllocator ? PooledByteBufAllocator.DEFAULT :
                UnpooledByteBufAllocator.DEFAULT, encoder) {
            @Override
            protected void handleException(Throwable t) {
                handleUnexpectedException(t);
            }
        };
        if (typePollution) {
            for (int i = 0; i < 20000; i++) {
                differentTypes();
            }
        }
    }

    @TearDown(Level.Trial)
    public void teardown() {
        content.release();
        content = null;
    }

    @Benchmark
    public void fullMessage() throws Exception {
        fullRequest.content().setIndex(0, contentBytes);
        encoder.write(context, fullRequest, newPromise());
    }

    @Benchmark
    public void contentLength() throws Exception {
        encoder.write(context, contentLengthRequest, newPromise());
        lastContent.content().setIndex(0, contentBytes);
        encoder.write(context, lastContent, newPromise());
    }

    @Benchmark
    public void chunked() throws Exception {
        encoder.write(context, chunkedRequest, newPromise());
        lastContent.content().setIndex(0, contentBytes);
        encoder.write(context, lastContent, newPromise());
    }

    @Benchmark
    public void differentTypes() throws Exception {
        encoder.write(context, contentLengthRequest, newPromise());
        lastContent.content().setIndex(0, contentBytes);
        encoder.write(context, lastContent, newPromise());
        content.setIndex(0, contentBytes);
        fullRequest.content().setIndex(0, contentBytes);
        encoder.write(context, fullRequest, newPromise());
        encoder.write(context, chunkedRequest, newPromise());
        lastContent.content().setIndex(0, contentBytes);
        encoder.write(context, lastContent, newPromise());
    }

    private ChannelPromise newPromise() {
        return voidPromise ? context.voidPromise() : context.newPromise();
    }
}
