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

import com.google.caliper.ConfiguredBenchmark;
import com.google.caliper.Environment;
import com.google.caliper.EnvironmentGetter;
import com.google.caliper.Json;
import com.google.caliper.Result;
import com.google.caliper.Run;
import com.google.caliper.Runner;
import com.google.caliper.Scenario;
import com.google.caliper.ScenarioResult;
import com.google.caliper.SimpleBenchmark;
import com.yammer.metrics.core.TimerContext;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Custom caliper runner for {@link CaliperBench}.
 */
public final class CaliperRunner {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(CaliperRunner.class);

    private CaliperRunner() {
    }

    /**
     * Parse bench parameters.
     */
    public static List<String> valueList(final String valueText) {
        return Arrays.asList(valueText.split(","));
    }

    /**
     * Execute full cycle: warm up, execute and publish benchmark.
     */
    public static void execute(final Class<? extends CaliperBench> klaz)
            throws Exception {
        execute("WARMUP", klaz);
        Run run = execute("REPORT", klaz);
        publish(newResult(run));
    }

    /**
     * Execute benchmark for all parameter combinations.
     */
    public static Run execute(final String name,
            final Class<? extends CaliperBench> klaz) throws Exception {

        final CaliperBench booter = klaz.getConstructor().newInstance();

        final List<Map<String, String>> varsSet = product(booter);

        final Run run = newRun(klaz.getName());

        int index = 0;
        for (final Map<String, String> vars : varsSet) {
            final int done = 100 * index++ / varsSet.size();

            log.info("{} {}% {}", name, done, vars);

            /** call setUp() */
            final ConfiguredBenchmark runner = booter.createBenchmark(vars);

            final CaliperBench bench = (CaliperBench) runner.getBenchmark();
            final CaliperMeasure measure = bench.measure();
            measure.variables().putAll(vars);

            /** call timeXXX() */
            runner.run(0);

            /** call tearDown() */
            runner.close();

            measure.appendTo(run);
        }

        return run;
    }

    /**
     * Convert caliper result into JSON string.
     */
    public static String json(final Result result) {
        return Json.getGsonInstance().toJson(result);
    }

    /**
     * Map signature based on map values.
     */
    public static String signature(final Map<String, String> map) {
        final StringBuilder text = new StringBuilder();
        for (final String item : map.values()) {
            text.append(String.format("%20s", item));
        }
        return text.toString();
    }

    /**
     * Generate all parameter combinations for {@link SimpleBenchmark}.
     */
    public static List<Map<String, String>> product(final SimpleBenchmark bench) {
        final Set<Map<String, String>> collect = new HashSet<Map<String, String>>();
        final Map<String, Set<String>> pending = new TreeMap<String, Set<String>>();
        for (final String name : new TreeSet<String>(bench.parameterNames())) {
            pending.put(name, bench.parameterValues(name));
        }
        final List<Map<String, String>> list = new ArrayList<Map<String, String>>(
                product(collect, pending));
        final Comparator<Map<String, String>> comp = new Comparator<Map<String, String>>() {
            @Override
            public int compare(final Map<String, String> o1,
                    final Map<String, String> o2) {
                return signature(o1).compareTo(signature(o2));
            }
        };
        Collections.sort(list, comp);
        return list;
    }

    /**
     * Calculate ordered Cartesian product of sets.
     */
    public static Set<Map<String, String>> product(
            final Set<Map<String, String>> collect,
            final Map<String, Set<String>> pending) {

        if (pending.isEmpty()) {
            return collect;
        }

        final Set<Map<String, String>> extract = new HashSet<Map<String, String>>();
        final String key = pending.keySet().iterator().next();
        for (final String value : pending.remove(key)) {
            final Map<String, String> map = new TreeMap<String, String>();
            map.put(key, value);
            extract.add(map);
        }

        if (collect.isEmpty()) {
            collect.addAll(extract);
            return product(collect, pending);
        } else {
            final Set<Map<String, String>> inject = new HashSet<Map<String, String>>();
            for (final Map<String, String> mapExtr : extract) {
                for (final Map<String, String> mapColl : collect) {
                    final Map<String, String> mapProd = new TreeMap<String, String>();
                    mapProd.putAll(mapExtr);
                    mapProd.putAll(mapColl);
                    inject.add(mapProd);
                }
            }
            return product(inject, pending);
        }
    }

    /**
     * Publish result on https://microbenchmarks.appspot.com
     */
    public static void publish(final Result result) throws Exception {
        final Runner runner = new Runner();
        final Method method = runner.getClass().getDeclaredMethod(
                "postResults", Result.class);
        method.setAccessible(true);
        method.invoke(runner, result);
    }

    /**
     * Provide new named run instance.
     */
    public static Run newRun(final String benchmarkName) {
        final Map<Scenario, ScenarioResult> measurements = new HashMap<Scenario, ScenarioResult>();
        final Date executedTimestamp = new Date();
        return new Run(measurements, benchmarkName, executedTimestamp);
    }

    /**
     * Make new result from run.
     */
    public static Result newResult(final Run run) {
        final Environment env = new EnvironmentGetter().getEnvironmentSnapshot();
        return new Result(run, env);
    }

    /**
     * Verify measure publication manually.
     */
    public static void main(final String[] args) throws Exception {
        final Run run = newRun("test-main");
        for (int param = 0; param < 5; param++) {
            final CaliperMeasure measure = new CaliperMeasure();
            measure.variables().put("param", String.valueOf(param));
            for (int step = 0; step < 5; step++) {
                measure.rate().mark(50 + step);
                final TimerContext time = measure.time().time();
                Thread.sleep(15);
                time.stop();
                measure.size().value(50 + step);
                measure.mark();
            }
            measure.appendTo(run);
        }
        final Result result = newResult(run);
        publish(result);
        System.out.println(json(result));
    }

}
