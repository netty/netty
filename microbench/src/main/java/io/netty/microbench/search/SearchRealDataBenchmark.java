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
package io.netty.microbench.search;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.search.AbstractMultiSearchProcessorFactory;
import io.netty.buffer.search.AbstractSearchProcessorFactory;
import io.netty.buffer.search.SearchProcessor;
import io.netty.buffer.search.SearchProcessorFactory;
import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.internal.ResourcesUtil;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.CompilerControl.Mode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5)
@Measurement(iterations = 5)
@Fork(1)
public class SearchRealDataBenchmark extends AbstractMicrobenchmark {

    public enum Algorithm {
        AHO_CORASIC {
            @Override
            SearchProcessorFactory newFactory(byte[] needle) {
                return AbstractMultiSearchProcessorFactory.newAhoCorasicSearchProcessorFactory(needle);
            }
        },
        KMP {
            @Override
            SearchProcessorFactory newFactory(byte[] needle) {
                return AbstractSearchProcessorFactory.newKmpSearchProcessorFactory(needle);
            }
        },
        BITAP {
            @Override
            SearchProcessorFactory newFactory(byte[] needle) {
                return AbstractSearchProcessorFactory.newBitapSearchProcessorFactory(needle);
            }
        };
        abstract SearchProcessorFactory newFactory(byte[] needle);
    }

    @Param
    public Algorithm algorithm;

    @Param
    public ByteBufType bufferType;

    private ByteBuf haystack;
    private SearchProcessorFactory[] searchProcessorFactories;
    private SearchProcessorFactory searchProcessorFactory;

    private static final byte[][] NEEDLES = {
            "Thank You".getBytes(),
            "* Does not exist *".getBytes(),
            "<li>".getBytes(),
            "<body>".getBytes(),
            "</li>".getBytes(),
            "github.com".getBytes(),
            " Does not exist 2 ".getBytes(),
            "</html>".getBytes(),
            "\"https://".getBytes(),
            "Netty 4.1.45.Final released".getBytes()
    };

    private int needleId, searchFrom, haystackLength;

    @Setup
    public void setup() throws IOException {
        File haystackFile = ResourcesUtil.getFile(SearchRealDataBenchmark.class, "netty-io-news.html");
        byte[] haystackBytes = readBytes(haystackFile);
        haystack = bufferType.newBuffer(haystackBytes);

        needleId = 0;
        searchFrom = 0;
        haystackLength = haystack.readableBytes();

        searchProcessorFactories = new SearchProcessorFactory[NEEDLES.length];
        for (int i = 0; i < NEEDLES.length; i++) {
            searchProcessorFactories[i] = algorithm.newFactory(NEEDLES[i]);
        }
    }

    @Setup(Level.Invocation)
    public void invocationSetup() {
        needleId = (needleId + 1) % searchProcessorFactories.length;
        searchProcessorFactory = searchProcessorFactories[needleId];
    }

    @TearDown
    public void teardown() {
        haystack.release();
    }

    @Benchmark
    @CompilerControl(Mode.DONT_INLINE)
    public int findFirst() {
        return haystack.forEachByte(searchProcessorFactory.newSearchProcessor());
    }

    @Benchmark
    @CompilerControl(Mode.DONT_INLINE)
    public int findFirstFromIndex() {
        searchFrom = (searchFrom + 100) % haystackLength;
        return haystack.forEachByte(
                searchFrom, haystackLength - searchFrom, searchProcessorFactory.newSearchProcessor());
    }

    @Benchmark
    @CompilerControl(Mode.DONT_INLINE)
    public void findAll(Blackhole blackHole) {
        SearchProcessor searchProcessor = searchProcessorFactory.newSearchProcessor();
        int pos = 0;
        do {
            pos = haystack.forEachByte(pos, haystackLength - pos, searchProcessor) + 1;
            blackHole.consume(pos);
        } while (pos > 0);
    }

    private static byte[] readBytes(File file) throws IOException {
        InputStream in = new FileInputStream(file);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                byte[] buf = new byte[8192];
                for (;;) {
                    int ret = in.read(buf);
                    if (ret < 0) {
                        break;
                    }
                    out.write(buf, 0, ret);
                }
                return out.toByteArray();
            } finally {
                safeClose(out);
            }
        } finally {
            safeClose(in);
        }
    }

    private static void safeClose(InputStream in) {
        try {
            in.close();
        } catch (IOException ignored) { }
    }

    private static void safeClose(OutputStream out) {
        try {
            out.close();
        } catch (IOException ignored) { }
    }

}
