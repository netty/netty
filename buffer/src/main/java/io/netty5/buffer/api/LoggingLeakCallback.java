/*
 * Copyright 2021 The Netty Project
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
package io.netty5.buffer.api;

import io.netty5.util.internal.logging.InternalLogger;
import io.netty5.util.internal.logging.InternalLoggerFactory;

import java.util.function.Consumer;

/**
 * The {@link LoggingLeakCallback} can be {@linkplain MemoryManager#onLeakDetected(Consumer) installed} to enable
 * logging output when a leak is detected.
 * <p>
 * The logging output will be done with the {@code ERROR} level.
 * <p>
 * Note that asynchronous and fast logging should be preferred, since the callback may run inside a cleaner-thread.
 */
public final class LoggingLeakCallback implements Consumer<LeakInfo> {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(LoggingLeakCallback.class);
    private static final LoggingLeakCallback instance = new LoggingLeakCallback();

    /**
     * Get an instance of the {@link LoggingLeakCallback}.
     *
     * @return An instance of the {@link LoggingLeakCallback} class.
     */
    public static LoggingLeakCallback getInstance() {
        return instance;
    }

    private LoggingLeakCallback() {
    }

    @Override
    public void accept(LeakInfo leakInfo) {
        if (logger.isErrorEnabled()) {
            String message = "LEAK: Object \"" + leakInfo.objectDescription() + "\" was not property closed before " +
                             "it was garbage collected. " +
                             "A life-cycle back-trace (if any) is attached as suppressed exceptions. " +
                             "See https://netty.io/wiki/reference-counted-objects.html for more information.";
            logger.error(message, LeakReport.reportFor(leakInfo));
        }
    }

    private static final class LeakReport extends Throwable {
        private static final long serialVersionUID = -1894217374238341652L;

        static LeakReport reportFor(LeakInfo info) {
            LeakReport report = new LeakReport();
            info.forEach(tracePoint -> report.addSuppressed(tracePoint.traceback()));
            return report;
        }

        private LeakReport() {
            super("Object life-cycle trace:", null, true, false);
        }
    }
}
