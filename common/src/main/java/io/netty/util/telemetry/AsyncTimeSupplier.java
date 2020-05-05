package io.netty.util.telemetry;

import java.util.concurrent.atomic.AtomicLong;

public final class AsyncTimeSupplier extends Thread implements TimeSupplier {
    private static AsyncTimeSupplier instance;

    public static synchronized AsyncTimeSupplier getInstance() {
        if (instance == null) {
            instance = new AsyncTimeSupplier();
            instance.start();
        }
        return instance;
    }

    public static synchronized void pause() {
        instance.interrupt();
        instance = null;
    }

    private final AtomicLong time = new AtomicLong();

    private AsyncTimeSupplier() {
        setDaemon(true);
    }

    @Override
    public long currentTime() {
        return time.getAcquire();
    }

    @Override
    public void run() {
        while (!isInterrupted()) {
            time.setRelease(SyncTimeSupplier.INSTANCE.currentTime());
        }
    }
}
