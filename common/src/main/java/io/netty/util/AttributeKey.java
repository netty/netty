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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Key which can be used to access {@link Attribute} out of the {@link AttributeMap}. Be aware that it is not be
 * possible to have multiple keys with the same name.
 *
 *
 * @param <T>   the type of the {@link Attribute} which can be accessed via this {@link AttributeKey}.
 */
public final class AttributeKey<T> extends UniqueName {

    private static final ConcurrentMap<String, Boolean> names = new ConcurrentHashMap<String, Boolean>();

    /**
     * Create a new instance
     *
     * @param name  the name under which the {@link AttributeKey} will be registered
     */
    public AttributeKey(String name) {
        super(names, name);
    }
}
