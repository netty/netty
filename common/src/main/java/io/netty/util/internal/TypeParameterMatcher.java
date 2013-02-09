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

package io.netty.util.internal;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public abstract class TypeParameterMatcher {

    private static final TypeParameterMatcher NOOP = new NoOpTypeParameterMatcher();

    private static final ThreadLocal<Map<Class<?>, TypeParameterMatcher>> typeMap =
            new ThreadLocal<Map<Class<?>, TypeParameterMatcher>>() {
                @Override
                protected Map<Class<?>, TypeParameterMatcher> initialValue() {
                    return new IdentityHashMap<Class<?>, TypeParameterMatcher>();
                }
            };

    public static TypeParameterMatcher find(
            final Object object, final Class<?> parameterizedSuperClass, final int typeParamIndex) {

        final Map<Class<?>, TypeParameterMatcher> typeMap = TypeParameterMatcher.typeMap.get();
        final Class<?> thisClass = object.getClass();

        TypeParameterMatcher matcher = typeMap.get(thisClass);
        if (matcher == null) {
            Class<?> currentClass = thisClass;
            for (;;) {
                if (currentClass.getSuperclass() == parameterizedSuperClass) {
                    Type[] types = ((ParameterizedType) currentClass.getGenericSuperclass()).getActualTypeArguments();
                    if (types.length - 1 < typeParamIndex) {
                        List<Type> typeList = new ArrayList<Type>(types.length);
                        Collections.addAll(typeList, types);
                        throw new IllegalStateException(
                                "invalid typeParamIndex: " + typeParamIndex + " (typeParams: " + typeList + ')');
                    }

                    Type t = types[typeParamIndex];
                    Class<?> messageType;
                    if (t instanceof Class) {
                        messageType = (Class<?>) t;
                    } else if (t instanceof ParameterizedType) {
                        messageType = (Class<?>) ((ParameterizedType) t).getRawType();
                    } else {
                        throw new IllegalStateException(
                                "cannot determine the type of the type parameter of " +
                                thisClass.getSimpleName() + ": " + t);
                    }

                    if (messageType == Object.class) {
                        matcher = NOOP;
                    } else if (PlatformDependent.hasJavassist()) {
                        try {
                            matcher = JavassistTypeParameterMatcherGenerator.generate(messageType);
                        } catch (Exception e) {
                            // Will not usually happen, but just in case.
                            matcher = new ReflectiveMatcher(messageType);
                        }
                    } else {
                        matcher = new ReflectiveMatcher(messageType);
                    }
                    break;
                }
                currentClass = currentClass.getSuperclass();
            }

            typeMap.put(thisClass, matcher);
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

    protected TypeParameterMatcher() { }
}
