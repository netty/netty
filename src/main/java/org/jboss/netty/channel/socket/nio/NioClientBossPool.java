/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.channel.socket.nio;

import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.ThreadNameDeterminer;
import org.jboss.netty.util.Timer;

import java.util.concurrent.Executor;

/**
 * Holds {@link NioClientBoss} instances to use
 */
public class NioClientBossPool extends AbstractNioBossPool<NioClientBoss> {
    private final ThreadNameDeterminer determiner;
    private final Timer timer;
    private boolean stopTimer;

    /**
     * Create a new instance
     *
     * @param bossExecutor  the Executor to use for server the {@link NioClientBoss}
     * @param bossCount     the number of {@link NioClientBoss} instances this {@link NioClientBossPool} will hold
     * @param timer         the Timer to use for handle connect timeouts
     * @param determiner    the {@link ThreadNameDeterminer} to use for name the threads. Use <code>null</code>
     *                      if you not want to set one explicit.
     */
    public NioClientBossPool(Executor bossExecutor, int bossCount, Timer timer, ThreadNameDeterminer determiner) {
        super(bossExecutor, bossCount, false);
        this.determiner = determiner;
        this.timer = timer;
        init();
    }

    /**
     * Create a new instance using a new {@link HashedWheelTimer} and no {@link ThreadNameDeterminer}
     *
     * @param bossExecutor  the Executor to use for server the {@link NioClientBoss}
     * @param bossCount     the number of {@link NioClientBoss} instances this {@link NioClientBoss} will hold
     */
    public NioClientBossPool(Executor bossExecutor, int bossCount) {
        this(bossExecutor, bossCount, new HashedWheelTimer(), null);
        stopTimer = true;
    }

    @Override
    protected NioClientBoss newBoss(Executor executor) {
        return new NioClientBoss(executor, timer, determiner);
    }

    @Override
    public void shutdown() {
        super.shutdown();
        if (stopTimer) {
            timer.stop();
        }
    }

    @Override
    public void releaseExternalResources() {
        super.releaseExternalResources();
        timer.stop();
    }
}

