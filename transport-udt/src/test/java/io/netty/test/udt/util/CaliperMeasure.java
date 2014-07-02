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

package io.netty.test.udt.util;

import com.google.caliper.Measurement;
import com.google.caliper.MeasurementSet;
import com.google.caliper.Run;
import com.google.caliper.Scenario;
import com.google.caliper.ScenarioResult;
import com.yammer.metrics.core.Gauge;
import com.yammer.metrics.core.Meter;
import com.yammer.metrics.core.MetricsRegistry;
import com.yammer.metrics.core.Timer;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Caliper measure with Metrics provider.
 * <p>
 * measure up to 3 values: {@link #rate()}, {@link #time()}, {@link #size()}
 */
public class CaliperMeasure {

    /**
     * Gauge any double value
     */
    public static class SizeGuage extends Gauge<Double> {

        private volatile Double size = 0.0;

        @Override
        public Double value() {
            return size;
        }

        public void value(final double size) {
            this.size = size;
        }
    }

    /**
     * Default rate measurement units.
     */
    private static final Map<String, Integer> RATE_UNIT = new HashMap<String, Integer>();
    static {
        RATE_UNIT.put("Rate  B/s", 1);
        RATE_UNIT.put("Rate KB/s", 1024);
        RATE_UNIT.put("Rate MB/s", 1024 * 1024);
        RATE_UNIT.put("Rate GB/s", 1024 * 1024 * 1024);
    }

    /**
     * Default time measurement units.
     */
    private static final Map<String, Integer> TIME_UNIT = new HashMap<String, Integer>();
    static {
        TIME_UNIT.put("Time ns", 1);
        TIME_UNIT.put("Time us", 1000);
        TIME_UNIT.put("Time ms", 1000 * 1000);
        TIME_UNIT.put("Time s ", 1000 * 1000 * 1000);
    }

    /**
     * Default size measurement units.
     */
    private static final Map<String, Integer> SIZE_UNIT = new HashMap<String, Integer>();
    static {
        SIZE_UNIT.put("Size  B", 1);
        SIZE_UNIT.put("Size KB", 1024);
        SIZE_UNIT.put("Size MB", 1024 * 1024);
        SIZE_UNIT.put("Size GB", 1024 * 1024 * 1024);
    }

    private final Map<Long, Measurement> rateMap = new HashMap<Long, Measurement>();
    private final Map<Long, Measurement> timeMap = new HashMap<Long, Measurement>();
    private final Map<Long, Measurement> sizeMap = new HashMap<Long, Measurement>();

    private final MetricsRegistry metrics = new MetricsRegistry();

    private final Meter rate = metrics.newMeter(getClass(), "rate", "bytes",
            TimeUnit.SECONDS);

    private final Timer time = metrics.newTimer(getClass(), "time",
            TimeUnit.NANOSECONDS, TimeUnit.SECONDS);

    private final SizeGuage size = new SizeGuage();
    {
        metrics.newGauge(getClass(), "", size);
    }

    /**
     * Rate meter.
     */
    public Meter rate() {
        return rate;
    }

    /**
     * Time meter.
     */
    public Timer time() {
        return time;
    }

    /**
     * Size meter.
     */
    public SizeGuage size() {
        return size;
    }

    /**
     * Workaround: zero breaks gwt web app.
     */
    private static double filter(final double value) {
        if (value <= 0.0) {
            return 1.0;
        } else {
            return value;
        }
    }

    /**
     * Perform measurement; convert from metrics into caliper.
     */
    @SuppressWarnings("FloatingPointEquality")
    public void mark() {
        final double rateValue = filter(rate.oneMinuteRate());
        final double timeValue = filter(time.mean());
        final double sizeValue = filter(size.value());
        if (rateValue == 1.0 && timeValue == 1.0 && sizeValue == 1.0) {
            /** ignore complete blank entries */
            return;
        }

        final Measurement markRate = new Measurement(RATE_UNIT, rateValue,
                rateValue);
        rateMap.put(System.nanoTime(), markRate);

        final Measurement markTime = new Measurement(TIME_UNIT, timeValue,
                timeValue);
        timeMap.put(System.nanoTime(), markTime);

        final Measurement markSize = new Measurement(SIZE_UNIT, sizeValue,
                sizeValue);
        sizeMap.put(System.nanoTime(), markSize);
    }

    private final Map<String, String> variables = new HashMap<String, String>();

    /**
     * Caliper scenario variables.
     */
    public Map<String, String> variables() {
        return variables;
    }

    private static MeasurementSet measurementSet(final Map<Long, Measurement> map) {
        final Measurement[] array = map.values().toArray(new Measurement[map.size()]);
        return new MeasurementSet(array);
    }

    /**
     * Attach this measure to parent caliper run.
     */
    public void appendTo(final Run run) {

        final Scenario scenario = new Scenario(variables());

        /** display rate as caliper durations */
        final MeasurementSet timeSet = measurementSet(rateMap);
        final String timeLog = null;

        /** display time as caliper instances */
        final MeasurementSet instSet = measurementSet(timeMap);
        final String instLog = null;

        /** display size as caliper memory */
        final MeasurementSet heapSet = measurementSet(sizeMap);
        final String heapLog = null;

        final ScenarioResult scenarioResult = new ScenarioResult(timeSet,
                timeLog, instSet, instLog, heapSet, heapLog);

        final Map<Scenario, ScenarioResult> measurements = run
                .getMeasurements();

        measurements.put(scenario, scenarioResult);
    }

    /**
     * Terminate metrics resources.
     */
    public void shutdown() {
        rate.stop();
        time.stop();
        metrics.shutdown();
    }
}
