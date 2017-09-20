/*
 * Copyright 2017 The Netty Project
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
package io.netty.microbench.util;

import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakHint;
import io.netty.util.ResourceLeakTracker;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;

public class ResourceLeakDetectorRecordBenchmark extends AbstractMicrobenchmark {
    private static final Object TRACKED = new Object();
    private static final ResourceLeakHint HINT = new ResourceLeakHint() {
        @Override
        public String toHintString() {
            return "BenchmarkHint";
        }
    };

    @Param({ "8", "16" })
    private int recordTimes;
    private ResourceLeakDetector.Level level;

    ResourceLeakDetector<Object> detector = new ResourceLeakDetector<Object>(
            Object.class, 1, Integer.MAX_VALUE) {
        @Override
        protected void reportTracedLeak(String resourceType, String records) {
            // noop
        }

        @Override
        protected void reportUntracedLeak(String resourceType) {
            // noop
        }

        @Override
        protected void reportInstancesLeak(String resourceType) {
            // noop
        }
    };

    @Setup(Level.Trial)
    public void setup() {
        level = ResourceLeakDetector.getLevel();
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID);
    }

    @TearDown(Level.Trial)
    public void teardown() {
        ResourceLeakDetector.setLevel(level);
    }

    @Benchmark
    public boolean record() {
        ResourceLeakTracker<Object> tracker = detector.track(TRACKED);
        for (int i = 0 ; i < recordTimes; i++) {
            tracker.record();
        }
        return tracker.close(TRACKED);
    }

    @Benchmark
    public boolean recordWithHint() {
        ResourceLeakTracker<Object> tracker = detector.track(TRACKED);
        for (int i = 0 ; i < recordTimes; i++) {
            tracker.record(HINT);
        }
        return tracker.close(TRACKED);
    }
}
