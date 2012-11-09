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
package io.netty.monitor.yammer;

import io.netty.monitor.EventRateMonitor;

import com.yammer.metrics.core.Meter;

/**
 * <p>
 * An {@link EventRateMonitor} that delegates to a <a
 * href="http://metrics.codahale.com/">Yammer</a> {@link Meter}.
 * </p>
 */
class YammerEventRateMonitor implements EventRateMonitor {

    private final Meter delegate;

    /**
     * @param delegate
     */
    YammerEventRateMonitor(final Meter delegate) {
        if (delegate == null) {
            throw new NullPointerException("delegate");
        }
        this.delegate = delegate;
    }

    /**
     * @see io.netty.monitor.EventRateMonitor#event()
     * @see com.yammer.metrics.core.Meter#mark()
     */
    @Override
    public void event() {
        delegate.mark();
    }

    /**
     * @see io.netty.monitor.EventRateMonitor#events(long)
     * @see com.yammer.metrics.core.Meter#mark(long)
     */
    @Override
    public void events(final long count) {
        delegate.mark(count);
    }

    /**
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "YammerEventRateMonitor(delegate=" + delegate + ')';
    }
}
