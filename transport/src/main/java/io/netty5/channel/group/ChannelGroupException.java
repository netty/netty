/*
 * Copyright 2013 The Netty Project
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
package io.netty5.channel.group;

import io.netty5.channel.Channel;
import io.netty5.channel.ChannelException;
import io.netty5.util.concurrent.Future;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * {@link ChannelException} which holds {@link Future}s that failed because of an error.
 */
public class ChannelGroupException extends ChannelException implements Iterable<Map.Entry<Channel, Throwable>> {
    private static final long serialVersionUID = -4093064295562629453L;
    private final Collection<Map.Entry<Channel, Throwable>> failed;

    public ChannelGroupException(Collection<Map.Entry<Channel, Throwable>> causes) {
        requireNonNull(causes, "causes");
        if (causes.isEmpty()) {
            throw new IllegalArgumentException("causes must be non empty");
        }
        failed = Collections.unmodifiableCollection(causes);
    }

    /**
     * Returns a {@link Iterator} which contains all the {@link Throwable} that was a cause of the failure and the
     * related id of the {@link Channel}.
     */
    @Override
    public Iterator<Map.Entry<Channel, Throwable>> iterator() {
        return failed.iterator();
    }
}
