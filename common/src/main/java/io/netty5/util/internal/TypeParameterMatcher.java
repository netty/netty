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

package io.netty5.util.internal;

import io.netty5.util.concurrent.FastThreadLocal;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

public abstract class TypeParameterMatcher {

    private static final FastThreadLocal<Map<Class<?>, TypeParameterMatcher>> TYPE_MATCHER_GET_CACHE =
            new FastThreadLocal<>() {
                @Override
                protected Map<Class<?>, TypeParameterMatcher> initialValue() {
                    return new IdentityHashMap<>();
                }
            };

    private static final FastThreadLocal<Map<Class<?>, Map<String, TypeParameterMatcher>>> TYPE_MATCHER_FIND_CACHE =
            new FastThreadLocal<>() {
                @Override
                protected Map<Class<?>, Map<String, TypeParameterMatcher>> initialValue() {
                    return new IdentityHashMap<>();
                }
            };

    private static final TypeParameterMatcher NOOP = new TypeParameterMatcher() {
        @Override
        public boolean match(Object msg) {
            return true;
        }
    };

    public static TypeParameterMatcher get(final Class<?> parameterType) {
        final Map<Class<?>, TypeParameterMatcher> getCache = TYPE_MATCHER_GET_CACHE.get();

        TypeParameterMatcher matcher = getCache.get(parameterType);
        if (matcher == null) {
            if (parameterType == Object.class) {
                matcher = NOOP;
            } else {
                matcher = new ReflectiveMatcher(parameterType);
            }
            getCache.put(parameterType, matcher);
        }

        return matcher;
    }

    public static TypeParameterMatcher find(
            final Object object, final Class<?> parametrizedSuperclass, final String typeParamName) {

        final Map<Class<?>, Map<String, TypeParameterMatcher>> findCache = TYPE_MATCHER_FIND_CACHE.get();
        final Class<?> thisClass = object.getClass();

        Map<String, TypeParameterMatcher> map = findCache.computeIfAbsent(thisClass, k -> new HashMap<>());

        TypeParameterMatcher matcher = map.get(typeParamName);
        if (matcher == null) {
            matcher = get(ReflectionUtil.resolveTypeParameter(object, parametrizedSuperclass, typeParamName));
            map.put(typeParamName, matcher);
        }

        return matcher;
    }

    public abstract boolean match(Object msg);

    private static final class ReflectiveMatcher extends TypeParameterMatcher {
        private final Class<?> type;

        ReflectiveMatcher(Class<?> type) {
            this.type = type;
        }

        @Override
        public boolean match(Object msg) {
            return type.isInstance(msg);
        }
    }

    TypeParameterMatcher() { }
}
