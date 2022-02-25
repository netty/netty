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

import io.netty5.buffer.api.Drop;

import java.util.function.Function;

/**
 * Utility class to capture or hold on to a drop instance.
 *
 * @param <T> The type argument to {@link Drop}.
 */
public final class DropCaptor<T> implements Function<Drop<T>, Drop<T>> {
    private Drop<T> drop;

    public Drop<T> capture(Drop<T> drop) {
        this.drop = drop;
        return drop;
    }

    @Override
    public Drop<T> apply(Drop<T> drop) {
        return capture(drop);
    }

    public Drop<T> getDrop() {
        return drop;
    }
}
