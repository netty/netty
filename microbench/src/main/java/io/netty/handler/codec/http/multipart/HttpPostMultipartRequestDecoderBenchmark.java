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
package io.netty.handler.codec.http.multipart;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.CharsetUtil;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetector.Level;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;


@Threads(1)
@Warmup(iterations = 2)
@Measurement(iterations = 3)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class HttpPostMultipartRequestDecoderBenchmark
        extends AbstractMicrobenchmark {

    public double testHighNumberChunks(boolean big, boolean noDisk) {
        String BOUNDARY = "01f136d9282f";
        int size = 8 * 1024;
        int chunkNumber = 64;
        StringBuilder stringBuilder = new StringBuilder(size);
        stringBuilder.setLength(size);
        String data = stringBuilder.toString();

        byte[] bodyStartBytes = ("--" + BOUNDARY + "\n" +
                                 "Content-Disposition: form-data; name=\"msg_id\"\n\n15200\n--" +
                                 BOUNDARY +
                                 "\nContent-Disposition: form-data; name=\"msg1\"; filename=\"file1.txt\"\n\n" +
                                 data).getBytes(CharsetUtil.UTF_8);
        byte[] bodyPartBigBytes = data.getBytes(CharsetUtil.UTF_8);
        byte[] intermediaryBytes = ("\n--" + BOUNDARY +
                                    "\nContent-Disposition: form-data; name=\"msg2\"; filename=\"file2.txt\"\n\n" +
                                    data).getBytes(CharsetUtil.UTF_8);
        byte[] finalBigBytes = ("\n" + "--" + BOUNDARY + "--\n").getBytes(CharsetUtil.UTF_8);
        ByteBuf firstBuf = Unpooled.wrappedBuffer(bodyStartBytes);
        ByteBuf finalBuf = Unpooled.wrappedBuffer(finalBigBytes);
        ByteBuf nextBuf;
        if (big) {
            nextBuf = Unpooled.wrappedBuffer(bodyPartBigBytes);
        } else {
            nextBuf = Unpooled.wrappedBuffer(intermediaryBytes);
        }
        DefaultHttpRequest req =
                new DefaultHttpRequest(HttpVersion.HTTP_1_0, HttpMethod.POST, "/up");
        req.headers().add(HttpHeaderNames.CONTENT_TYPE,
                          "multipart/form-data; boundary=" + BOUNDARY);

        long start = System.nanoTime();

        DefaultHttpDataFactory defaultHttpDataFactory =
                new DefaultHttpDataFactory(noDisk? 1024 * 1024 : 16 * 1024);
        HttpPostRequestDecoder decoder =
                new HttpPostRequestDecoder(defaultHttpDataFactory, req);
        firstBuf.retain();
        decoder.offer(new DefaultHttpContent(firstBuf));
        firstBuf.release();
        for (int i = 1; i < chunkNumber; i++) {
            nextBuf.retain();
            decoder.offer(new DefaultHttpContent(nextBuf));
            nextBuf.release();
            nextBuf.readerIndex(0);
        }
        finalBuf.retain();
        decoder.offer(new DefaultLastHttpContent(finalBuf));
        finalBuf.release();
        while (decoder.hasNext()) {
            InterfaceHttpData httpData = decoder.next();
        }
        while (finalBuf.refCnt() > 0) {
            finalBuf.release();
        }
        while (nextBuf.refCnt() > 0) {
            nextBuf.release();
        }
        while (finalBuf.refCnt() > 0) {
            finalBuf.release();
        }
        long stop = System.nanoTime();
        double time = (stop - start) / 1000000.0;
        defaultHttpDataFactory.cleanAllHttpData();
        defaultHttpDataFactory.cleanRequestHttpData(req);
        decoder.destroy();
        return time;
    }

    @Benchmark
    public double multipartRequestDecoderHighDisabledLevel() {
        final Level level = ResourceLeakDetector.getLevel();
        try {
            ResourceLeakDetector.setLevel(Level.DISABLED);
            return testHighNumberChunks(false, true);
        } finally {
            ResourceLeakDetector.setLevel(level);
        }
    }

    @Benchmark
    public double multipartRequestDecoderBigDisabledLevel() {
        final Level level = ResourceLeakDetector.getLevel();
        try {
            ResourceLeakDetector.setLevel(Level.DISABLED);
            return testHighNumberChunks(true, true);
        } finally {
            ResourceLeakDetector.setLevel(level);
        }
    }

    @Benchmark
    public double multipartRequestDecoderHighSimpleLevel() {
        final Level level = ResourceLeakDetector.getLevel();
        try {
            ResourceLeakDetector.setLevel(Level.SIMPLE);
            return testHighNumberChunks(false, true);
        } finally {
            ResourceLeakDetector.setLevel(level);
        }
    }

    @Benchmark
    public double multipartRequestDecoderBigSimpleLevel() {
        final Level level = ResourceLeakDetector.getLevel();
        try {
            ResourceLeakDetector.setLevel(Level.SIMPLE);
            return testHighNumberChunks(true, true);
        } finally {
            ResourceLeakDetector.setLevel(level);
        }
    }

    @Benchmark
    public double multipartRequestDecoderHighAdvancedLevel() {
        final Level level = ResourceLeakDetector.getLevel();
        try {
            ResourceLeakDetector.setLevel(Level.ADVANCED);
            return testHighNumberChunks(false, true);
        } finally {
            ResourceLeakDetector.setLevel(level);
        }
    }

    @Benchmark
    public double multipartRequestDecoderBigAdvancedLevel() {
        final Level level = ResourceLeakDetector.getLevel();
        try {
            ResourceLeakDetector.setLevel(Level.ADVANCED);
            return testHighNumberChunks(true, true);
        } finally {
            ResourceLeakDetector.setLevel(level);
        }
    }

    @Benchmark
    public double multipartRequestDecoderHighParanoidLevel() {
        final Level level = ResourceLeakDetector.getLevel();
        try {
            ResourceLeakDetector.setLevel(Level.PARANOID);
            return testHighNumberChunks(false, true);
        } finally {
            ResourceLeakDetector.setLevel(level);
        }
    }

    @Benchmark
    public double multipartRequestDecoderBigParanoidLevel() {
        final Level level = ResourceLeakDetector.getLevel();
        try {
            ResourceLeakDetector.setLevel(Level.PARANOID);
            return testHighNumberChunks(true, true);
        } finally {
            ResourceLeakDetector.setLevel(level);
        }
    }

}
