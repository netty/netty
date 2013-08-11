/*
 * Copyright 2013 The Netty Project
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
package io.netty.dns;

import io.netty.buffer.ByteBuf;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * Returns a single result, as opposed to a {@link List} of results in a {@link DnsCallback}.
 *
 * @param <T>
 *            a single result for a specified type (i.e. if type is A, result would be a {@link ByteBuf})
 */
public class DnsSingleResultCallback<T> implements Callable<T> {

    private final DnsCallback<List<T>> parent;

    /**
     * Constructs a {@link DnsSingleResultCallback} by passing it a {@link DnsCallback}.
     *
     * @param parent
     *            the {@link DnsCallback}
     */
    public DnsSingleResultCallback(DnsCallback<List<T>> parent) {
        this.parent = parent;
    }

    /**
     * Invokes the {@link DnsCallback}'s {@link DnsCallback#call()} method and returns a result based on the resolver's
     * {@link DnsSelectionStrategy}, if it exists, or else {@code null}.
     */
    @Override
    public T call() throws InterruptedException {
        List<T> list = parent.call();
        if (list == null || list.isEmpty()) {
            return null;
        }
        return parent.resolver().selector().selectRecord(list);
    }

}
