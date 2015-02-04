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
package io.netty.microbench.util;

import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.internal.SystemPropertyUtil;

import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;

/**
 * Default implementation of the JMH microbenchmark adapter.  There may be context switches introduced by this harness.
 */
@Fork(AbstractMicrobenchmark.DEFAULT_FORKS)
public class AbstractMicrobenchmark extends AbstractMicrobenchmarkBase {

    protected static final int DEFAULT_FORKS = 2;
    protected static final String[] JVM_ARGS;

    static {
        final String[] customArgs = {
        "-Xms768m", "-Xmx768m", "-XX:MaxDirectMemorySize=768m", "-Dharness.executor=CUSTOM",
        "-Dharness.executor.class=io.netty.microbench.util.AbstractMicrobenchmark$HarnessExecutor" };

        JVM_ARGS = new String[BASE_JVM_ARGS.length + customArgs.length];
        System.arraycopy(BASE_JVM_ARGS, 0, JVM_ARGS, 0, BASE_JVM_ARGS.length);
        System.arraycopy(customArgs, 0, JVM_ARGS, BASE_JVM_ARGS.length, customArgs.length);
    }

    public static final class HarnessExecutor extends ThreadPoolExecutor {
        public HarnessExecutor(int maxThreads, String prefix) {
            super(0, maxThreads, 1L, TimeUnit.DAYS, new SynchronousQueue<Runnable>(),
                  new DefaultThreadFactory(prefix));
            System.out.println("Using harness executor");
        }
    }

    @Override
    protected String[] jvmArgs() {
        return JVM_ARGS;
    }

    protected ChainedOptionsBuilder newOptionsBuilder() throws Exception {
        ChainedOptionsBuilder runnerOptions = super.newOptionsBuilder();

        if (getForks() > 0) {
            runnerOptions.forks(getForks());
        }

        return runnerOptions;
    }

    protected int getForks() {
        return SystemPropertyUtil.getInt("forks", -1);
    }
}
