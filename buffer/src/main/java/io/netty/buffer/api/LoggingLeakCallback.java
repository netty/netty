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
package io.netty.buffer.api;

import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.function.Consumer;

public class LoggingLeakCallback implements Consumer<LeakInfo> {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(LoggingLeakCallback.class);

    @Override
    public void accept(LeakInfo leakInfo) {
        if (logger.isErrorEnabled()) {
            logger.error(new LeakReport(leakInfo));
        }
    }

    private static final class LeakReport extends Throwable {
        private static final long serialVersionUID = -1894217374238341652L;

        LeakReport(LeakInfo leakInfo) {
            super("LEAK: buffer (of " + leakInfo.bytesLeaked() + " bytes) was not property closed before it was " +
                  "garbage collected. " +
                  "A life-cycle back-trace (if any) is attached as suppressed exceptions. " +
                  "See https://netty.io/wiki/reference-counted-objects.html for more information.", null, true, false);
            leakInfo.forEach(tracePoint -> addSuppressed(tracePoint.getTraceback()));
        }
    }
}
