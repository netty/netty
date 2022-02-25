/*
 * Copyright 2021 The Netty Project
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
package io.netty5.buffer.api.tests;

import io.netty5.buffer.api.BufferAllocator;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.function.Supplier;

public final class Fixture implements Supplier<BufferAllocator> {
    private final String name;
    private final Supplier<BufferAllocator> factory;
    private final EnumSet<Properties> properties;

    public Fixture(String name, Supplier<BufferAllocator> factory, Properties... props) {
        this.name = name;
        this.factory = factory;
        properties = EnumSet.copyOf(Arrays.asList(props));
    }

    public BufferAllocator createAllocator() {
        return factory.get();
    }

    @Override
    public BufferAllocator get() {
        return factory.get();
    }

    @Override
    public String toString() {
        return name;
    }

    public Properties[] getProperties() {
        return properties.toArray(Properties[]::new);
    }

    public boolean isHeap() {
        return properties.contains(Properties.HEAP);
    }

    public boolean isDirect() {
        return properties.contains(Properties.DIRECT);
    }

    public boolean isComposite() {
        return properties.contains(Properties.COMPOSITE);
    }

    public boolean isPooled() {
        return properties.contains(Properties.POOLED);
    }

    public enum Properties {
        HEAP,
        DIRECT,
        COMPOSITE,
        POOLED
    }
}
