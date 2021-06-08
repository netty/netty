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
package io.netty.buffer.api.internal;

import io.netty.buffer.api.Resource;
import io.netty.buffer.api.Scope;

import java.util.ArrayList;

/**
 * A {@link Scope} implementation based on {@link ArrayList}.
 */
public class ArrayListScope extends ArrayList<Resource<?>> implements Scope {
    private static final long serialVersionUID = 3093047762719644781L;

    @Override
    public <T extends Resource<T>> T add(T obj) {
        super.add(obj);
        return obj;
    }

    @Override
    public void close() {
        while (!isEmpty()) {
            remove(size() - 1).close();
        }
    }
}
