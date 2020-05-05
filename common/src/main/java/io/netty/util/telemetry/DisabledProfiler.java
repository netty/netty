package io.netty.util.telemetry;

public final class DisabledProfiler implements Profiler {
    public static final DisabledProfiler INSTANCE = new DisabledProfiler();

    private DisabledProfiler() {
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    @Override
    public String print() {
        throw new DisabledException();
    }
}
