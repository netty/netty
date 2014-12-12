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
package io.netty.handler.codec.http.routing;

import java.util.Map;

public class Routed<T> {
    private final T                   target;
    private final boolean             notFound;
    private final Map<String, String> params;

    public Routed(T target, boolean notFound, Map<String, String> params) {
        this.target   = target;
        this.notFound = notFound;
        this.params   = params;
    }

    public T target() {
        return target;
    }

    public boolean notFound() {
        return notFound;
    }

    public Map<String, String> params() {
        return params;
    }

    /**
     * When target is a class, this method calls "newInstance" on the class.
     * Otherwise it returns the target as is.
     *
     * @return null if target is null
     */
    public static Object instanceFromTarget(Object target) throws InstantiationException, IllegalAccessException {
        if (target == null) { return null; }

        if (target instanceof Class) {
            // Create handler from class
            Class<?> klass = (Class<?>) target;
            return klass.newInstance();
        } else {
            return target;
        }
    }

    /**
     * When target is a class, this method calls "newInstance" on the class.
     * Otherwise it returns the target as is.
     *
     * @return null if target is null
     */
    public Object instanceFromTarget() throws InstantiationException, IllegalAccessException {
        return Routed.instanceFromTarget(target());
    }
}
