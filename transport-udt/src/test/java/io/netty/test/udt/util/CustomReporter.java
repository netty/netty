/*
 * Copyright 2012 The Netty Project
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

package io.netty.test.udt.util;

import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.Clock;
import com.yammer.metrics.core.Counter;
import com.yammer.metrics.core.Gauge;
import com.yammer.metrics.core.Histogram;
import com.yammer.metrics.core.Metered;
import com.yammer.metrics.core.Metric;
import com.yammer.metrics.core.MetricName;
import com.yammer.metrics.core.MetricPredicate;
import com.yammer.metrics.core.MetricProcessor;
import com.yammer.metrics.core.MetricsRegistry;
import com.yammer.metrics.core.Timer;
import com.yammer.metrics.reporting.AbstractPollingReporter;
import com.yammer.metrics.stats.Snapshot;

import java.io.PrintStream;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * A simple reporters which prints out application metrics to a
 * {@link PrintStream} periodically.
 */
public class CustomReporter extends AbstractPollingReporter implements
        MetricProcessor<PrintStream> {
    private static final int CONSOLE_WIDTH = 80;

    /**
     * Enables the console reporter for the default metrics registry, and causes
     * it to print to STDOUT with the specified period.
     */
    public static void enable(final long period, final TimeUnit unit) {
        enable(Metrics.defaultRegistry(), period, unit);
    }

    /**
     * Enables the console reporter for the given metrics registry, and causes
     * it to print to STDOUT with the specified period and unrestricted output.
     */
    public static void enable(final MetricsRegistry metricsRegistry,
            final long period, final TimeUnit unit) {
        final CustomReporter reporter = new CustomReporter(
                metricsRegistry, System.out, MetricPredicate.ALL);
        reporter.start(period, unit);
    }

    private final PrintStream out;
    private final MetricPredicate predicate;
    private final Clock clock;
    private final TimeZone timeZone;
    private final Locale locale;

    /**
     * Creates a new {@link CustomReporter} for the default metrics
     * registry, with unrestricted output.
     */
    public CustomReporter(final PrintStream out) {
        this(Metrics.defaultRegistry(), out, MetricPredicate.ALL);
    }

    /**
     * Creates a new {@link CustomReporter} for a given metrics registry.
     */
    public CustomReporter(final MetricsRegistry metricsRegistry,
            final PrintStream out, final MetricPredicate predicate) {
        this(metricsRegistry, out, predicate, Clock.defaultClock(), TimeZone
                .getDefault());
    }

    /**
     * Creates a new {@link CustomReporter} for a given metrics registry.
     */
    public CustomReporter(final MetricsRegistry metricsRegistry,
            final PrintStream out, final MetricPredicate predicate,
            final Clock clock, final TimeZone timeZone) {
        this(metricsRegistry, out, predicate, clock, timeZone, Locale
                .getDefault());
    }

    /**
     * Creates a new {@link CustomReporter} for a given metrics registry.
     */
    public CustomReporter(final MetricsRegistry metricsRegistry,
            final PrintStream out, final MetricPredicate predicate,
            final Clock clock, final TimeZone timeZone, final Locale locale) {
        super(metricsRegistry, "console-reporter");
        this.out = out;
        this.predicate = predicate;
        this.clock = clock;
        this.timeZone = timeZone;
        this.locale = locale;
    }

    @Override
    public void run() {
        try {
            final DateFormat format = DateFormat.getDateTimeInstance(
                    DateFormat.SHORT, DateFormat.MEDIUM, locale);
            format.setTimeZone(timeZone);
            final String dateTime = format.format(new Date(clock.time()));
            out.print(dateTime);
            out.print(' ');
            for (int i = 0; i < CONSOLE_WIDTH - dateTime.length() - 1; i++) {
                out.print('=');
            }
            out.println();
            for (final Entry<String, SortedMap<MetricName, Metric>> entry : getMetricsRegistry()
                    .groupedMetrics(predicate).entrySet()) {
                out.print(entry.getKey());
                out.println(':');
                for (final Entry<MetricName, Metric> subEntry : entry
                        .getValue().entrySet()) {
                    out.print("  ");
                    out.print(subEntry.getKey().getName());
                    out.println(':');
                    subEntry.getValue().processWith(this, subEntry.getKey(),
                            out);
                    out.println();
                }
                out.println();
            }
            out.println();
            out.flush();
        } catch (final Exception e) {
            e.printStackTrace(out);
        }
    }

    @Override
    public void processGauge(final MetricName name, final Gauge<?> gauge,
            final PrintStream stream) {
        stream.printf(locale, "    value = %s\n", gauge.value());
    }

    @Override
    public void processCounter(final MetricName name, final Counter counter,
            final PrintStream stream) {
        stream.printf(locale, "    count = %,d\n", counter.count());
    }

    @Override
    public void processMeter(final MetricName name, final Metered meter,
            final PrintStream stream) {
        final String unit = abbrev(meter.rateUnit());
        stream.printf(locale, "             count = %,d\n", meter.count());
        stream.printf(locale, "         mean rate = %,2.2f %s/%s\n",
                meter.meanRate(), meter.eventType(), unit);
        stream.printf(locale, "     1-minute rate = %,2.2f %s/%s\n",
                meter.oneMinuteRate(), meter.eventType(), unit);
        stream.printf(locale, "     5-minute rate = %,2.2f %s/%s\n",
                meter.fiveMinuteRate(), meter.eventType(), unit);
        stream.printf(locale, "    15-minute rate = %,2.2f %s/%s\n",
                meter.fifteenMinuteRate(), meter.eventType(), unit);
    }

    @Override
    public void processHistogram(final MetricName name,
            final Histogram histogram, final PrintStream stream) {
        final Snapshot snapshot = histogram.getSnapshot();
        stream.printf(locale, "               min = %,2.2f\n", histogram.min());
        stream.printf(locale, "               max = %,2.2f\n", histogram.max());
        stream.printf(locale, "              mean = %,2.2f\n", histogram.mean());
        stream.printf(locale, "            stddev = %,2.2f\n",
                histogram.stdDev());
        stream.printf(locale, "            median = %,2.2f\n",
                snapshot.getMedian());
        stream.printf(locale, "              75%% <= %,2.2f\n",
                snapshot.get75thPercentile());
        stream.printf(locale, "              95%% <= %,2.2f\n",
                snapshot.get95thPercentile());
        stream.printf(locale, "              98%% <= %,2.2f\n",
                snapshot.get98thPercentile());
        stream.printf(locale, "              99%% <= %,2.2f\n",
                snapshot.get99thPercentile());
        stream.printf(locale, "            99.9%% <= %,2.2f\n",
                snapshot.get999thPercentile());
    }

    @Override
    public void processTimer(final MetricName name, final Timer timer,
            final PrintStream stream) {
        processMeter(name, timer, stream);
        final String durationUnit = abbrev(timer.durationUnit());
        final Snapshot snapshot = timer.getSnapshot();
        stream.printf(locale, "               min = %,2.2f %s\n", timer.min(),
                durationUnit);
        stream.printf(locale, "               max = %,2.2f %s\n", timer.max(),
                durationUnit);
        stream.printf(locale, "              mean = %,2.2f %s\n", timer.mean(),
                durationUnit);
        stream.printf(locale, "            stddev = %,2.2f %s\n",
                timer.stdDev(), durationUnit);
        stream.printf(locale, "            median = %,2.2f %s\n",
                snapshot.getMedian(), durationUnit);
        stream.printf(locale, "              75%% <= %,2.2f %s\n",
                snapshot.get75thPercentile(), durationUnit);
        stream.printf(locale, "              95%% <= %,2.2f %s\n",
                snapshot.get95thPercentile(), durationUnit);
        stream.printf(locale, "              98%% <= %,2.2f %s\n",
                snapshot.get98thPercentile(), durationUnit);
        stream.printf(locale, "              99%% <= %,2.2f %s\n",
                snapshot.get99thPercentile(), durationUnit);
        stream.printf(locale, "            99.9%% <= %,2.2f %s\n",
                snapshot.get999thPercentile(), durationUnit);
    }

    private static String abbrev(final TimeUnit unit) {
        switch (unit) {
        case NANOSECONDS:
            return "ns";
        case MICROSECONDS:
            return "us";
        case MILLISECONDS:
            return "ms";
        case SECONDS:
            return "s";
        case MINUTES:
            return "m";
        case HOURS:
            return "h";
        case DAYS:
            return "d";
        default:
            throw new IllegalArgumentException("Unrecognized TimeUnit: " + unit);
        }
    }
}
