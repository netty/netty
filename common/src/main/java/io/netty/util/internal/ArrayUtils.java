/*
 * Copyright 2016 The Netty Project
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

import java.lang.reflect.Array;

import static io.netty.util.internal.ObjectUtil.*;

public final class ArrayUtils {
    private ArrayUtils() { }

    public static <T> T[] join(Class<? super T> type, T[]... arrays) {
        checkNotNull(arrays, "Null array of arrays");
        Class<?> componentType = arrays.getClass().getComponentType().getComponentType();
        checkNotNull(type, "Null type");
        if (!type.isAssignableFrom(componentType)) {
            throw new IllegalArgumentException(unexpectedArrayTypeMsg(type, componentType));
        }
        if (arrays.length == 0) {
            return (T[]) Array.newInstance(type, 0);
        } else if (arrays.length == 1) {
            return checkNotNull(arrays[0], "Null array").clone();
        }
        int resultSize = 0;
        for (T[] array : arrays) {
            resultSize += array.length;
        }
        @SuppressWarnings("unchecked") // Its fine -_-
        T[] result = (T[]) Array.newInstance(checkNotNull(type, "Null type"), resultSize);
        int index = 0;
        for (T[] array : arrays) {
            int arrayLength = array.length;
            System.arraycopy(array, 0, result, index, arrayLength);
            index += arrayLength;
        }
        return result;
    }

    private static String unexpectedArrayTypeMsg(Class<?> expectedType, Class<?> arrayType) {
        return "Array of type "
               + arrayType.getSimpleName()
               + "  doesn't match specified type "
               + expectedType.getSimpleName();
    }

}
