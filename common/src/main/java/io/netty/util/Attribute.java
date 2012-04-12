package io.netty.util;

public interface Attribute<T> {
    T get();
    void set(T value);
    T getAndSet(T value);
    T setIfAbsent(T value);
    boolean compareAndSet(T oldValue, T newValue);
    void remove();
}
