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

package io.netty.bench;

import io.netty.bench.util.NettyBenchmark;
import io.netty.bench.util.NetworkUtil;

import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.TreeSet;

import org.reflections.Reflections;

/**
 * Bench tool entry point.
 */
public final class Main {

    private Main() {
    }

    /**
     * Discover and execute benchmarks.
     */
    public static void main(final String[] args) {
        // discover
        final Reflections reflections = new Reflections("");
        final Set<Class<? extends NettyBenchmark>> subTypes = reflections
                .getSubTypesOf(NettyBenchmark.class);

        // prepare
        final Set<NettyBenchmark> benchSet = new TreeSet<NettyBenchmark>();
        for (final Class<? extends NettyBenchmark> benchClass : subTypes) {
            if (Modifier.isAbstract(benchClass.getModifiers())) {
                continue;
            }
            try {
                final NettyBenchmark bench = benchClass.newInstance();
                benchSet.add(bench);
            } catch (final Throwable e) {
                NetworkUtil.log("invalid bench class : " + benchClass + " : "
                        + e.getMessage());
            }
        }

        // execute
        for (final NettyBenchmark bench : benchSet) {
            try {
                bench.execute();
            } catch (final Throwable e) {
                NetworkUtil.log("bench execution failure : " + bench.getClass()
                        + " : " + e.getMessage());
            }
        }
    }

}
