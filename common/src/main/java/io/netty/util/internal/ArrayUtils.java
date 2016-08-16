package io.netty.util.internal;

import java.lang.reflect.Array;

import static io.netty.util.internal.ObjectUtil.*;

public class ArrayUtils {
    private ArrayUtils() {}

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
        for (T[] array : arrays) resultSize += array.length;
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
        return "Array of type " + arrayType.getSimpleName() + "  doesn't match specified type " + expectedType.getSimpleName();
    }

}
