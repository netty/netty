/*
 * Copyright 2012 The Netty Project
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

package io.netty.transport.udt.util;

import io.netty.logging.InternalLoggerFactory;
import io.netty.logging.Slf4JLoggerFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.caliper.SimpleBenchmark;

/**
 * Base class for caliper/metrics benchmarks.
 */
public abstract class CaliperBench extends SimpleBenchmark {

    /**
     * Ensure no network latency after JVM shutdown
     */
    static {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    TrafficControl.delay(0);
                } catch (final Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * Use slf4j logging.
     */
    static {
        final InternalLoggerFactory defaultFactory = new Slf4JLoggerFactory();
        InternalLoggerFactory.setDefaultFactory(defaultFactory);
    }

    protected final Logger log = LoggerFactory.getLogger(getClass());

    private volatile CaliperMeasure measure;

    /**
     * Caliper metrics wrapper.
     */
    protected CaliperMeasure measure() {
        return measure;
    }

    /**
     * Start measurement.
     */
    @Override
    protected void setUp() throws Exception {
        measure = new CaliperMeasure();
    }

    /**
     * Finish measurement.
     */
    @Override
    protected void tearDown() throws Exception {
        measure.shutdown();
    }

    /**
     * Measure time step and minimum run time.
     */
    protected long markStep() {
        return 3 * 1000;
    }

    /**
     * Measure progress while in sleep.
     */
    protected void markWait(final long time) throws Exception {

        final long timeStart = System.currentTimeMillis();

        while (true) {
            Thread.sleep(markStep());
            measure().mark();
            final long timeFinish = System.currentTimeMillis();
            if (timeFinish - timeStart >= time) {
                System.out.print("+\n");
                return;
            } else {
                System.out.print("-");
                continue;
            }
        }

    }

}
