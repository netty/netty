package io.netty.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.Closeable;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertThrows;

class LeakPresenceDetectorTest {
    private ResourceLeakDetectorFactory oldFactory;

    private void setUp(BiFunction<Class<?>, Integer, LeakPresenceDetector<?>> factory) {
        oldFactory = ResourceLeakDetectorFactory.instance();
        ResourceLeakDetectorFactory.setResourceLeakDetectorFactory(new ResourceLeakDetectorFactory() {
            @SuppressWarnings("unchecked")
            @Override
            public <T> ResourceLeakDetector<T> newResourceLeakDetector(Class<T> resource, int samplingInterval, long maxActive) {
                return (ResourceLeakDetector<T>) factory.apply(resource, samplingInterval);
            }
        });
    }

    @AfterEach
    public void tearDown() {
        ResourceLeakDetectorFactory.setResourceLeakDetectorFactory(oldFactory);
    }

    static List<BiFunction<Class<?>, Integer, LeakPresenceDetector<?>>> factories() {
        return Arrays.asList(LeakPresenceDetector.Global::new, LeakPresenceDetector.ThreadLocal::new);
    }

    @ParameterizedTest
    @MethodSource("factories")
    public void noExceptionInNormalOperation(BiFunction<Class<?>, Integer, LeakPresenceDetector<?>> factory) {
        setUp(factory);

        Resource resource = new Resource();
        resource.close();
        resource.close();

        LeakPresenceDetector.check();
    }

    @SuppressWarnings("resource")
    @ParameterizedTest
    @MethodSource("factories")
    public void exceptionOnUnclosed(BiFunction<Class<?>, Integer, LeakPresenceDetector<?>> factory) {
        setUp(factory);

        new Resource();

        assertThrows(IllegalStateException.class, LeakPresenceDetector::check);
    }

    @Test
    public void noExceptionInStaticInitializerGlobal() {
        setUp(LeakPresenceDetector.Global::new);

        ResourceInStaticVariable1.init();

        LeakPresenceDetector.check();
    }

    @Test
    public void noExceptionInStaticInitializerThreadLocal() {
        setUp(LeakPresenceDetector.ThreadLocal::new);

        ResourceInStaticVariable2.init();

        LeakPresenceDetector.check();
    }

    private static class Resource implements Closeable {
        private final ResourceLeakTracker<Resource> leak;

        Resource() {
            leak = ResourceLeakDetectorFactory.instance().newResourceLeakDetector(Resource.class).track(this);
        }

        @Override
        public void close() {
            leak.close(this);
        }
    }

    @SuppressWarnings("unused")
    private static class ResourceInStaticVariable1 {
        private static final Resource RESOURCE = LeakPresenceDetector.staticInitializer(Resource::new);

        static void init() {
        }
    }

    @SuppressWarnings("unused")
    private static class ResourceInStaticVariable2 {
        private static final Resource RESOURCE = LeakPresenceDetector.staticInitializer(Resource::new);

        static void init() {
        }
    }
}