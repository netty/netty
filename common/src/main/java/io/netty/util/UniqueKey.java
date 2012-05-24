package io.netty.util;

import java.util.concurrent.ConcurrentMap;

public class UniqueKey<T> extends UniqueName {

    private final Class<T> valueType;
    private final String strVal;

    public UniqueKey(ConcurrentMap<String, Boolean> map, String name, Class<T> valueType) {
        super(map, name, valueType);
        this.valueType = valueType;
        strVal = name + '[' + valueType.getSimpleName() + ']';
    }

    @Override
    protected void validateArgs(Object... args) {
        super.validateArgs(args);
        if (args[0] == null) {
            throw new NullPointerException("valueType");
        }
    }

    public final Class<T> valueType() {
        return valueType;
    }

    @Override
    public String toString() {
        return strVal;
    }
}
