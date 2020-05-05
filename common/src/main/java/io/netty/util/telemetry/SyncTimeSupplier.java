package io.netty.util.telemetry;

public final class SyncTimeSupplier implements TimeSupplier {
    public static final SyncTimeSupplier INSTANCE = new SyncTimeSupplier();

    private SyncTimeSupplier() {
    }

    @Override
    public long currentTime() {
        return System.nanoTime();
    }
}
