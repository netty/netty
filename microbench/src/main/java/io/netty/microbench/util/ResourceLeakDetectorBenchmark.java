/*
 * Copyright 2015 The Netty Project
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
package io.netty.microbench.util;

import io.netty.util.ResourceLeakDetector;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Setup;

public class ResourceLeakDetectorBenchmark extends AbstractMicrobenchmark {

    private static final Object DUMMY = new Object();
    private ResourceLeakDetector<Object> detector;

    @Setup
    public void setup() {
        detector = new ResourceLeakDetector<Object>(getClass(), 128, Long.MAX_VALUE);
    }

    @Benchmark
    public Object open() {
        return detector.open(DUMMY);
    }
}
