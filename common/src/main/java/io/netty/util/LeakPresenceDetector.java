package io.netty.util;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

public abstract class LeakPresenceDetector<T> extends ResourceLeakDetector<T> {
    private static int staticInitializerCount;

    private static boolean inStaticInitializerSlow() {
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            if (element.getMethodName().equals("<clinit>")) {
                return true;
            }
        }
        return false;
    }

    private static boolean inStaticInitializerFast() {
        // This plain field access is safe. TODO: explain why :)
        return staticInitializerCount != 0 && inStaticInitializerSlow();
    }

    public static <R> R staticInitializer(Supplier<R> supplier) {
        synchronized (LeakPresenceDetector.class) {
            staticInitializerCount++;
        }
        try {
            return supplier.get();
        } finally {
            synchronized (LeakPresenceDetector.class) {
                staticInitializerCount--;
            }
        }
    }

    LeakPresenceDetector(Class<?> resourceType) {
        super(resourceType, 0);
    }

    abstract LongAdder openCount();

    @Override
    public ResourceLeakTracker<T> track(T obj) {
        if (inStaticInitializerFast()) {
            return null;
        }
        return trackForcibly(obj);
    }

    @Override
    public ResourceLeakTracker<T> trackForcibly(T obj) {
        return new PresenceTracker<>(openCount());
    }

    public static void check() {
        // for LeakPresenceDetector, this is cheap.
        ResourceLeakDetector<Object> detector = ResourceLeakDetectorFactory.instance()
                .newResourceLeakDetector(Object.class);

        if (!(detector instanceof LeakPresenceDetector)) {
            throw new IllegalStateException("LeakPresenceDetector not in use. Please register it using -Dio.netty.customResourceLeakDetector=io.netty.util.LeakPresenceDetector$Global");
        }

        LongAdder counter = ((LeakPresenceDetector<Object>) detector).openCount();
        if (counter.sumThenReset() != 0) {
            throw new IllegalStateException("Possible memory leak detected. Please use paranoid leak detection to get more information.");
        }
    }

    public static final class Global<T> extends LeakPresenceDetector<T> {
        private static final LongAdder COUNT = new LongAdder();

        @SuppressWarnings("unused")
        public Global(Class<?> resourceType, int samplingInterval) {
            super(resourceType);
        }

        @SuppressWarnings("unused")
        public Global(Class<?> resourceType, int samplingInterval, long maxActive) {
            super(resourceType);
        }

        @Override
        LongAdder openCount() {
            return COUNT;
        }
    }

    public static final class ThreadLocal<T> extends LeakPresenceDetector<T> {
        private static final InheritableThreadLocal<LongAdder> COUNT = new InheritableThreadLocal<LongAdder>() {
            @Override
            protected LongAdder initialValue() {
                return new LongAdder();
            }
        };

        @SuppressWarnings("unused")
        public ThreadLocal(Class<?> resourceType, int samplingInterval) {
            super(resourceType);
        }

        @SuppressWarnings("unused")
        public ThreadLocal(Class<?> resourceType, int samplingInterval, long maxActive) {
            super(resourceType);
        }

        @Override
        LongAdder openCount() {
            return COUNT.get();
        }
    }

    private static final class PresenceTracker<T> extends AtomicBoolean implements ResourceLeakTracker<T> {
        private final LongAdder counter;

        private PresenceTracker(LongAdder counter) {
            super(false);
            this.counter = counter;
            counter.increment();
        }

        @Override
        public void record() {
        }

        @Override
        public void record(Object hint) {
        }

        @Override
        public boolean close(Object trackedObject) {
            if (compareAndSet(false, true)) {
                counter.decrement();
                return true;
            } else {
                return false;
            }
        }
    }
}
