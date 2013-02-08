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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class TypeParameterFinder {

    // TODO: Use a weak key map
    private static final ConcurrentMap<Class<?>, Class<?>> typeMap = new ConcurrentHashMap<Class<?>, Class<?>>();

    public static Class<?> findActualTypeParameter(
            final Object object, final Class<?> parameterizedSuperClass, final int typeParamIndex) {
        final Class<?> thisClass = object.getClass();
        Class<?> messageType = typeMap.get(thisClass);
        if (messageType == null) {
            Class<?> currentClass = thisClass;
            for (;;) {
                if (currentClass.getSuperclass() == parameterizedSuperClass) {
                    Type[] types = ((ParameterizedType) currentClass.getGenericSuperclass()).getActualTypeArguments();
                    if (types.length - 1 < typeParamIndex || !(types[0] instanceof Class)) {
                        throw new IllegalStateException(
                                "cannot determine the inbound message type of " + thisClass.getSimpleName());
                    }

                    messageType = (Class<?>) types[0];
                    break;
                }
                currentClass = currentClass.getSuperclass();
            }

            typeMap.put(thisClass, messageType);
        }

        return messageType;
    }


    private TypeParameterFinder() { }
}
