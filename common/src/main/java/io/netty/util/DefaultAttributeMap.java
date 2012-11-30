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
package io.netty.util;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class DefaultAttributeMap implements AttributeMap {

    // Initialize lazily to reduce memory consumption.
    private Map<AttributeKey<?>, Attribute<?>> map;

    @Override
    public synchronized <T> Attribute<T> attr(AttributeKey<T> key) {
        Map<AttributeKey<?>, Attribute<?>> map = this.map;
        if (map == null) {
            // Not using ConcurrentHashMap due to high memory consumption.
            map = this.map = new IdentityHashMap<AttributeKey<?>, Attribute<?>>(2);
        }

        @SuppressWarnings("unchecked")
        Attribute<T> attr = (Attribute<T>) map.get(key);
        if (attr == null) {
            attr = new DefaultAttribute<T>();
            map.put(key, attr);
        }
        return attr;
    }

    private class DefaultAttribute<T> extends AtomicReference<T> implements Attribute<T> {

        private static final long serialVersionUID = -2661411462200283011L;

        @Override
        public T setIfAbsent(T value) {
            if (compareAndSet(null, value)) {
                return null;
            } else {
                return get();
            }
        }

        @Override
        public void remove() {
            set(null);
        }
    }
}
