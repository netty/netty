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

package io.netty.util;

import java.util.HashMap;
import java.util.Map;

/**
 * A pool of {@link Constant}s.
 *
 * @param <T> the type of the constant
 */
public abstract class ConstantPool<T extends Constant<T>> {

    private final Map<String, T> constants = new HashMap<String, T>();

    private int nextId = 1;

    /**
     * Creates a new or the existing {@link Constant} mapped to the specified {@code name}.
     */
    public T valueOf(String name) {
        if (name == null) {
            throw new NullPointerException("name");
        }

        synchronized (constants) {
            T c = constants.get(name);
            if (c == null) {
                c = newConstant(nextId, name);
                constants.put(name, c);
                nextId ++;
            }

            return c;
        }
    }

    protected abstract T newConstant(int id, String name);
}
