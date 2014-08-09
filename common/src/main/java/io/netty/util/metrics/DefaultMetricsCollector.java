/*
 * Copyright 2014 The Netty Project
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

package io.netty.util.metrics;

import io.netty.util.concurrent.EventExecutor;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.concurrent.TimeUnit;

public class DefaultMetricsCollector implements MetricsCollector {
    private boolean initialized;
    private int curTick;

    private int registeredChannels;
    private final CumulativeHistory bytesRead;
    private final CumulativeHistory bytesWritten;

    public DefaultMetricsCollector() {
        this.bytesRead = new CumulativeHistory(10);
        this.bytesWritten = new CumulativeHistory(10);
    }

    public <T extends EventExecutor> void init(T eventExecutor) {
        if (initialized) {
            throw new RuntimeException("init must not be called twice.");
        } else {
            initialized = true;
        }

        eventExecutor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                bytesRead.tick();
                bytesWritten.tick();
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    public void registerChannel() {
        ++registeredChannels;
    }

    public void unregisterChannel() {
        assert registeredChannels > 0;
        --registeredChannels;
    }

    public void readBytes(long bytes) {
        bytesRead.update(bytes);
    }

    public void writeBytes(long bytes) {
        bytesWritten.update(bytes);
    }

    public int registeredChannels() {
        return registeredChannels;
    }

    public long bytesRead() {
        return bytesRead.value();
    }

    public long bytesWritten() {
        return bytesWritten.value();
    }
}
