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

import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

public abstract class TypeParameterMatcher {

    private static final TypeParameterMatcher NOOP = new NoOpTypeParameterMatcher();
    private static final Object TEST_OBJECT = new Object();

    private static final ThreadLocal<Map<Class<?>, Map<String, TypeParameterMatcher>>> typeMap =
            new ThreadLocal<Map<Class<?>, Map<String, TypeParameterMatcher>>>() {
                @Override
                protected Map<Class<?>, Map<String, TypeParameterMatcher>> initialValue() {
                    return new IdentityHashMap<Class<?>, Map<String, TypeParameterMatcher>>();
                }
            };

    public static TypeParameterMatcher find(
            final Object object, final GenericDeclaration parameterizedSuperclass, final String typeParamName) {

        final Map<Class<?>, Map<String, TypeParameterMatcher>> typeMap = TypeParameterMatcher.typeMap.get();
        final Class<?> thisClass = object.getClass();

        Map<String, TypeParameterMatcher> map = typeMap.get(thisClass);
        if (map == null) {
            map = new HashMap<String, TypeParameterMatcher>();
            typeMap.put(thisClass, map);
        }

        TypeParameterMatcher matcher = map.get(typeParamName);
        if (matcher == null) {
            Class<?> messageType = find0(object, parameterizedSuperclass, typeParamName);
            if (messageType == Object.class) {
                matcher = NOOP;
            } else if (PlatformDependent.hasJavassist()) {
                try {
                    matcher = JavassistTypeParameterMatcherGenerator.generate(messageType);
                    matcher.match(TEST_OBJECT);
                } catch (IllegalAccessError e) {
                    // Happens if messageType is not public.
                    matcher = null;
                } catch (Exception e) {
                    // Will not usually happen, but just in case.
                    matcher = null;
                }
            }

            if (matcher == null) {
                matcher = new ReflectiveMatcher(messageType);
            }

            map.put(typeParamName, matcher);
        }

        return matcher;
    }

    private static Class<?> find0(
            final Object object, GenericDeclaration parameterizedSuperclass, String typeParamName) {

        final Class<?> thisClass = object.getClass();
        Class<?> currentClass = thisClass;
        for (;;) {
            if (currentClass.getSuperclass() == parameterizedSuperclass) {
                int typeParamIndex = -1;
                TypeVariable<?>[] typeParams = currentClass.getSuperclass().getTypeParameters();
                for (int i = 0; i < typeParams.length; i ++) {
                    if (typeParamName.equals(typeParams[i].getName())) {
                        typeParamIndex = i;
                        break;
                    }
                }

                if (typeParamIndex < 0) {
                    throw new IllegalStateException(
                            "unknown type parameter '" + typeParamName + "': " + parameterizedSuperclass);
                }

                Type[] actualTypeParams =
                        ((ParameterizedType) currentClass.getGenericSuperclass()).getActualTypeArguments();

                Type actualTypeParam = actualTypeParams[typeParamIndex];
                if (actualTypeParam instanceof Class) {
                    return (Class<?>) actualTypeParam;
                }
                if (actualTypeParam instanceof ParameterizedType) {
                    return (Class<?>) ((ParameterizedType) actualTypeParam).getRawType();
                }
                if (actualTypeParam instanceof TypeVariable) {
                    // Resolved type parameter points to another type parameter.
                    TypeVariable<?> v = (TypeVariable<?>) actualTypeParam;
                    currentClass = thisClass;
                    parameterizedSuperclass = v.getGenericDeclaration();
                    typeParamName = v.getName();
                    continue;
                }

                return fail(thisClass, typeParamName);
            }
            currentClass = currentClass.getSuperclass();
            if (currentClass == null) {
                return fail(thisClass, typeParamName);
            }
        }
    }

    private static Class<?> fail(Class<?> type, String typeParamName) {
        throw new IllegalStateException(
                "cannot determine the type of the type parameter '" + typeParamName + "': " + type);
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
