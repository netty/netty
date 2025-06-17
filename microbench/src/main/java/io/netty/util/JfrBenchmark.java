/*
 * Copyright 2025 The Netty Project
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
package io.netty.util;

import io.netty.microbench.util.AbstractMicrobenchmark;
import jdk.jfr.Event;
import jdk.jfr.consumer.RecordingStream;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;

import java.util.concurrent.TimeUnit;

@SuppressWarnings("Since15")
@Fork(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Measurement(time = 10, timeUnit = TimeUnit.SECONDS)
public class JfrBenchmark extends AbstractMicrobenchmark {
    @Param({"true", "false"})
    boolean enabled;

    private RecordingStream stream;

    @Setup
    public void setup() {
        stream = new RecordingStream();
        if (enabled) {
            stream.enable(NettyEvent.class.getName());
        } else {
            stream.disable(NettyEvent.class.getName());
        }
        stream.startAsync();
    }

    @TearDown
    public void teardown() {
        stream.close();
    }

    @Benchmark
    public void nettyEvent() {
        NettyEvent event = new NettyEvent();
        event.begin();
        event.end();
        event.foo = "bar";
        event.commit();
    }

    static final class NettyEvent extends Event {
        String foo;
    }
}
