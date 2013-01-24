package org.jboss.netty.channel.socket.nio;

import static org.junit.Assert.*;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jboss.netty.util.ThreadNameDeterminer;
import org.jboss.netty.util.internal.ExecutorUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class NioServerBossPoolTest {

    private static ExecutorService executor;

    @BeforeClass
    public static void init() {
        executor = Executors.newCachedThreadPool();
    }

    @AfterClass
    public static void destroy() {
        ExecutorUtil.terminate(executor);
    }

    private static final String MY_CUSTOM_THREAD_NAME = "FOO";

    private final ThreadNameDeterminer determiner = new ThreadNameDeterminer() {

        public String determineThreadName(String currentThreadName,
                String proposedThreadName) throws Exception {
            return MY_CUSTOM_THREAD_NAME;
        }

    };

    @Test
    public void testNioServerBossPoolExecutorIntThreadNameDeterminer()
            throws Exception {
        NioServerBossPool bossPool = null;
        try {
            bossPool = new NioServerBossPool(executor, 1, determiner);
            NioServerBoss nextBoss = bossPool.nextBoss();
            assertNotNull(nextBoss);
            // Wait for ThreadRenamingRunnable to be run by the executor
            Thread.sleep(1000);
            // Ok, now there should be thread
            assertNotNull(nextBoss.thread);
            assertEquals(MY_CUSTOM_THREAD_NAME, nextBoss.thread.getName());
        } finally {
            if (bossPool != null) {
                bossPool.shutdown();
            }
        }
    }
}
