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

import com.google.caliper.CaliperRc;
import com.google.caliper.Runner;
import com.google.caliper.SimpleBenchmark;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

public abstract class DefaultBenchmark extends SimpleBenchmark {

    private static boolean warned;

    private final int trials;
    private final int warmupMillis;
    private final int runMillis;

    protected DefaultBenchmark() {
        this(1);
    }

    protected DefaultBenchmark(int trials) {
        this(trials, 3000, 1000);
    }

    protected DefaultBenchmark(int trials, int warmupMillis, int runMillis) {
        this.trials = trials;
        this.warmupMillis = warmupMillis;
        this.runMillis = runMillis;
    }

    @Test
    public void runBenchmarks() throws Exception {
        File me = new File(DefaultBenchmark.class.getResource(
                '/' + DefaultBenchmark.class.getName().replace('.', '/') + ".class").getPath());

        if (!me.exists()) {
            fail("failed to determine the project path");
        }

        File buildDir =
                me.getParentFile().getParentFile().getParentFile().getParentFile().getParentFile().getParentFile();

        if (!buildDir.getPath().endsWith(File.separator + "target") || !buildDir.isDirectory()) {
            fail("failed to locate the build directory");
        }

        File reportDir = new File(buildDir.getAbsolutePath() + File.separator + "caliper-reports");

        if (!reportDir.exists()) {
            if (!reportDir.mkdirs()) {
                fail("failed to create the Caliper report directory: " + reportDir.getAbsolutePath());
            }
        }

        if (!reportDir.isDirectory()) {
            fail("not a directory: " + reportDir.getAbsolutePath());
        }

        boolean deleted = deleteOldReports(reportDir);

        if (!warned) {
            CaliperRc caliperrc = CaliperRc.INSTANCE;
            if (caliperrc.getApiKey() == null || caliperrc.getPostUrl() == null) {
                warned = true;
                System.out.println();
                System.out.println(" Cannot read the configuration properties from .caliperrc.");
                System.out.println(" Please follow the instructions at:");
                System.out.println();
                System.out.println("   * http://code.google.com/p/caliper/wiki/OnlineResults");
                System.out.println();
                System.out.println(" to upload and browse the benchmark results.");
            }
        }

        if (deleted || warned) {
            // Insert a pretty newline.
            System.out.println();
        }

        new Runner().run(
                "--trials", String.valueOf(trials),
                "--warmupMillis", String.valueOf(warmupMillis),
                "--runMillis", String.valueOf(runMillis),
                "--saveResults", reportDir.getAbsolutePath(),
                "--captureVmLog",
                getClass().getName());
    }

    private boolean deleteOldReports(File reportDir) {
        final String prefix = getClass().getName() + '.';
        final String suffix = ".json";

        boolean deleted = false;
        for (File f: reportDir.listFiles()) {
            String name = f.getName();
            if (name.startsWith(prefix) && name.endsWith(suffix)) {
                if (f.delete()) {
                    if (!deleted) {
                        deleted = true;
                        System.out.println();
                    }
                    System.out.println(" Deleted old report: " +
                            name.substring(prefix.length(), name.length() - suffix.length()));
                }
            }
        }

        return deleted;
    }
}
