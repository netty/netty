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

package io.netty.test.udt.bench;

import io.netty.test.udt.util.CaliperBench;
import io.netty.test.udt.util.CaliperRunner;
import io.netty.test.udt.util.TrafficControl;

import java.util.List;

/**
 * perform two way native udt socket send/recv
 */
public abstract class BenchXfer extends CaliperBench {

    /** introduce network latency */
    protected static List<String> latencyList() {
        if (TrafficControl.isAvailable()) {
            return CaliperRunner.valueList("0,10,30");
        } else {
            return CaliperRunner.valueList("0");
        }
    }

    /** verify different message sizes */
    protected static List<String> messageList() {
        return CaliperRunner
                .valueList("500,1500,3000,5000,10000,20000,50000,100000");
    }

    /** benchmark run time per each configuration */
    protected static List<String> durationList() {
        return CaliperRunner.valueList("30000");
    }

}
