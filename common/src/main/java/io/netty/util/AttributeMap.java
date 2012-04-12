package io.netty.util;

public interface AttributeMap {
    <T> Attribute<T> attr(AttributeKey key, Class<T> type);
}
