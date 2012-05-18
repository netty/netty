package io.netty.util;


import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class Signal extends Error {

    private static final long serialVersionUID = -221145131122459977L;

    private static final ConcurrentMap<String, Boolean> map =
            new ConcurrentHashMap<String, Boolean>();

    private final SignalName uname;

    public Signal(String name) {
        super(name);
        uname = new SignalName(name);
    }

    public void expect(Signal signal) {
        if (this != signal) {
            throw new IllegalStateException("unexpected signal: " + signal);
        }
    }

    @Override
    public Throwable initCause(Throwable cause) {
        return this;
    }

    @Override
    public Throwable fillInStackTrace() {
        return this;
    }

    @Override
    public String toString() {
        return uname.name();
    }

    private static class SignalName extends UniqueName {
        protected SignalName(String name) {
            super(map, name);
        }
    }
}
