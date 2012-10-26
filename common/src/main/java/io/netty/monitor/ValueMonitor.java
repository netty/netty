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
package io.netty.monitor;

/**
 * <p>
 * A monitor that tracks a datum's value. The datum to track may be of arbitrary
 * type.
 * </p>
 * <p>
 * <strong>DISCLAIMER</strong> This interface is heavily based on <a
 * href="http://metrics.codahale.com/">Yammer's</a>
 * {@link com.yammer.metrics.core.Gauge Gauge}.
 * </p>
 */
public interface ValueMonitor<T> {

    /**
     * Return our monitored datum's current value.
     * @return Our monitored datum's current value
     */
    T currentValue();
}
