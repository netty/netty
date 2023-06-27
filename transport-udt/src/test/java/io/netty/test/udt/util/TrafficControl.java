/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.test.udt.util;

import static io.netty.util.internal.ObjectUtil.checkPositiveOrZero;

import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

/**
 * Introduce traffic control, such as transfer latency.
 * <p>
 * requires sudo setup for /sbin/tc under current account
 * <p>
 * see https://www.davidverhasselt.com/2008/01/27/passwordless-sudo/
 */
public final class TrafficControl {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(TrafficControl.class);

    private TrafficControl() {
    }

    private static final String TC_DELAY = "sudo tc qdisc add dev %s root netem delay %sms limit %s";
    private static final String TC_RESET = "sudo tc qdisc del dev %s root";

    /**
     * verify if traffic control is available
     */
    public static boolean isAvailable() {
        try {
            final int millis = 100;
            final int margin = 20;
            delay(0);
            final long time1 = UnitHelp.ping("localhost");
            delay(millis);
            final long time2 = UnitHelp.ping("localhost");
            delay(0);
            final long time3 = UnitHelp.ping("localhost");
            return time2 >= time1 + millis - margin
                    && time2 >= time3 + millis - margin;
        } catch (final Throwable e) {
            log.debug("", e);
            return false;
        }
    }

    /**
     * Introduce round-trip delay on local host
     * @param time - delay in milliseconds; use zero to remove delay.
     */
    public static void delay(final int time) throws Exception {
        checkPositiveOrZero(time, "time");
        final int delay = time / 2;
        if (delay == 0) {
            UnitHelp.process(String.format(TC_RESET, "lo"));
        } else {
            /** extend packet buffer queue to avoid packet loss due to latency */
            final int limit = 1024 * 1024;
            UnitHelp.process(String.format(TC_RESET, "lo"));
            UnitHelp.process(String.format(TC_DELAY, "lo", delay, limit));
        }
    }

}
