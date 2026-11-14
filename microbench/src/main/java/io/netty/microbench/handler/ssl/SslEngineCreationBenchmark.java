/*
 * Copyright 2026 The Netty Project
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
package io.netty.microbench.handler.ssl;

import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.ReferenceCountUtil;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;

import java.io.File;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLEngine;

/**
 * Measures creating and releasing a server side OpenSSL engine, which a server does once for every accepted
 * connection.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class SslEngineCreationBenchmark extends AbstractMicrobenchmark {

    private final ByteBufAllocator allocator = UnpooledByteBufAllocator.DEFAULT;
    private SslContext serverContext;

    @Setup
    public void setup() throws Exception {
        File keyFile = new File(AbstractSslEngineBenchmark.class.getResource("test_unencrypted.pem").getFile());
        File crtFile = new File(AbstractSslEngineBenchmark.class.getResource("test.crt").getFile());
        serverContext = SslContextBuilder.forServer(crtFile, keyFile)
                .sslProvider(SslProvider.OPENSSL)
                .build();
    }

    @TearDown
    public void tearDown() {
        ReferenceCountUtil.release(serverContext);
    }

    @Override
    protected ChainedOptionsBuilder newOptionsBuilder() throws Exception {
        return super.newOptionsBuilder().addProfiler(GCProfiler.class);
    }

    @Benchmark
    public boolean newEngine() {
        SSLEngine engine = serverContext.newEngine(allocator);
        return ReferenceCountUtil.release(engine);
    }
}
