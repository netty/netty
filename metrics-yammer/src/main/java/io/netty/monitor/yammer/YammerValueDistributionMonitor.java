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

import io.netty.monitor.ValueDistributionMonitor;

import com.yammer.metrics.core.Histogram;

/**
 * <p>
 * An {@link ValueDistributionMonitor} that delegates to a <a
 * href="http://metrics.codahale.com/">Yammer</a> {@link Histogram}.
 * </p>
 */
final class YammerValueDistributionMonitor implements ValueDistributionMonitor {

    private final Histogram delegate;

    /**
     * @param delegate
     */
    YammerValueDistributionMonitor(final Histogram delegate) {
        if (delegate == null) {
            throw new NullPointerException("delegate");
        }
        this.delegate = delegate;
    }

    /**
     * @see io.netty.monitor.EventDistributionMonitor#reset()
     */
    @Override
    public void reset() {
        delegate.clear();
    }

    /**
     * @see io.netty.monitor.EventDistributionMonitor#update(long)
     */
    @Override
    public void update(final long value) {
        delegate.update(value);
    }

    /**
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "YammerEventDistributionMonitor(delegate=" + delegate + ')';
    }
}
