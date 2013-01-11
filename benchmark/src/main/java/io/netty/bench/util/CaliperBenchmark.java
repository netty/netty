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

package io.netty.bench.util;

import static io.netty.bench.util.NetworkUtil.*;

import java.io.File;

import com.google.caliper.CaliperRc;
import com.google.caliper.Runner;
import com.google.caliper.SimpleBenchmark;

/**
 * Base class for caliper-only benchmarks.
 */
public abstract class CaliperBenchmark extends SimpleBenchmark implements
        NettyBenchmark {

    private static boolean warned;

    private final int trials;
    private final int warmupMillis;
    private final int runMillis;

    protected CaliperBenchmark() {
        this(1);
    }

    protected CaliperBenchmark(final int trials) {
        this(trials, 3000, 1000);
    }

    protected CaliperBenchmark(final int trials, final int warmupMillis,
            final int runMillis) {
        this.trials = trials;
        this.warmupMillis = warmupMillis;
        this.runMillis = runMillis;
    }

    @Override
    public void execute() throws Exception {
        final File me = new File(CaliperBenchmark.class.getResource(
                '/' + CaliperBenchmark.class.getName().replace('.', '/')
                        + ".class").getPath());

        if (!me.exists()) {
            fail("failed to determine the project path");
        }

        final File buildDir = me.getParentFile().getParentFile()
                .getParentFile().getParentFile().getParentFile()
                .getParentFile();

        if (!buildDir.getPath().endsWith(File.separator + "target")
                || !buildDir.isDirectory()) {
            fail("failed to locate the build directory");
        }

        final File reportDir = new File(buildDir.getAbsolutePath()
                + File.separator + "caliper-reports");

        if (!reportDir.exists()) {
            if (!reportDir.mkdirs()) {
                fail("failed to create the Caliper report directory: "
                        + reportDir.getAbsolutePath());
            }
        }

        if (!reportDir.isDirectory()) {
            fail("not a directory: " + reportDir.getAbsolutePath());
        }

        final boolean deleted = deleteOldReports(reportDir);

        if (!warned) {
            final CaliperRc caliperrc = CaliperRc.INSTANCE;
            if (caliperrc.getApiKey() == null || caliperrc.getPostUrl() == null) {
                warned = true;
                log("#");
                log("# Cannot read the configuration properties from ${user.home}/.caliperrc");
                log("# Please follow the instructions at:");
                log("#    http://code.google.com/p/caliper/wiki/OnlineResults");
                log("# to upload and browse the benchmark results.");
                log("#");
            }
        }

        if (deleted || warned) {
            // Insert a pretty newline.
            log("");
        }

        new Runner().run("--trials", String.valueOf(trials), "--warmupMillis",
                String.valueOf(warmupMillis), "--runMillis", String
                        .valueOf(runMillis), "--saveResults", reportDir
                        .getAbsolutePath(), "--captureVmLog", getClass()
                        .getName());
    }

    private boolean deleteOldReports(final File reportDir) {
        final String prefix = getClass().getName() + '.';
        final String suffix = ".json";

        boolean deleted = false;
        for (final File f : reportDir.listFiles()) {
            final String name = f.getName();
            if (name.startsWith(prefix) && name.endsWith(suffix)) {
                if (f.delete()) {
                    if (!deleted) {
                        deleted = true;
                        log("");
                    }
                    log(" Deleted old report: "
                            + name.substring(prefix.length(), name.length()
                                    - suffix.length()));
                }
            }
        }

        return deleted;
    }

    /**
     * sort by full class name
     */
    @Override
    public int compareTo(final NettyBenchmark that) {
        return this.getClass().getName().compareTo(that.getClass().getName());
    }
}
