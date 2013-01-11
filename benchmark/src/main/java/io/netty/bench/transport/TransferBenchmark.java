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

package io.netty.bench.transport;

import io.netty.bench.util.CaliperBenchmark;
import io.netty.bench.util.NetworkUtil;
import io.netty.bench.util.TrafficControl;

import java.util.List;

/**
 * Native Socket Transfer Benchmark base.
 */
public abstract class TransferBenchmark extends CaliperBenchmark {

    public TransferBenchmark() {
        super(1, 10 * 1000, 2 * 1000);
    }

    protected static List<String> delayMillisValues() {
        if (TrafficControl.isAvailable()) {
            return NetworkUtil.list("0,10,20");
        } else {
            return NetworkUtil.list("0");
        }
    }

    protected static List<String> messageSizeValues() {
        return NetworkUtil.list("100,500,1500,3000,10000,50000");
    }

    protected enum Mode {
        BLOCKING, NON_BLOCKING
    }

}
