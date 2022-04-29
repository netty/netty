/*
 * Copyright 2022 The Netty Project
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
package io.netty5.buffer.api.internal;

import io.netty5.buffer.api.ComponentIterator;
import io.netty5.buffer.api.ComponentIterator.Next;
import io.netty5.buffer.api.Resource;

import java.lang.ref.Reference;

public final class SingleComponentIterator<T extends Next> implements ComponentIterator<T> {
    private final Resource<?> lifecycle;
    private final T singleComponent;

    @SuppressWarnings("unchecked")
    public SingleComponentIterator(Resource<?> lifecycle, Object singleComponent) {
        this.lifecycle = lifecycle;
        this.singleComponent = (T) singleComponent;
    }

    @Override
    public T first() {
        return singleComponent;
    }

    @Override
    public void close() {
        lifecycle.close();
        Reference.reachabilityFence(lifecycle);
    }
}
