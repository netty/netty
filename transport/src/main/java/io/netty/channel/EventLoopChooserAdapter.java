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

package io.netty.channel;

import io.netty.util.metrics.DefaultMetricsCollector;
import io.netty.util.metrics.MetricsCollector;

import java.net.SocketAddress;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public abstract class EventLoopChooserAdapter implements EventLoopChooser {

    protected final List<EventLoop> eventLoops = new CopyOnWriteArrayList<EventLoop>();
    protected final List<DefaultMetricsCollector> metrics = new CopyOnWriteArrayList<DefaultMetricsCollector>();

    @Override
    public void addChild(EventLoop eventLoop, MetricsCollector collector) {
        eventLoops.add(eventLoop);
        metrics.add((DefaultMetricsCollector) collector);
    }

    @Override
    public List<EventLoop> children() {
        return Collections.unmodifiableList(eventLoops);
    }

    @Override
    public MetricsCollector newMetricsCollector() {
        return new DefaultMetricsCollector();
    }

    @Override
    public abstract EventLoop next(SocketAddress remoteAddress);
}
