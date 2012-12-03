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

import org.jboss.netty.util.ThreadNameDeterminer;

import java.util.concurrent.Executor;


/**
 * Holds {@link NioServerBoss} instances to use
 */
public class NioServerBossPool extends AbstractNioBossPool<NioServerBoss> {
    private final ThreadNameDeterminer determiner;

    /**
     * Create a new instance
     *
     * @param bossExecutor  the {@link Executor} to use for server the {@link NioServerBoss}
     * @param bossCount     the number of {@link NioServerBoss} instances this {@link NioServerBossPool} will hold
     * @param determiner    the {@link ThreadNameDeterminer} to use for name the threads. Use <code>null</code>
     *                      if you not want to set one explicit.
     */
    public NioServerBossPool(Executor bossExecutor, int bossCount, ThreadNameDeterminer determiner) {
        super(bossExecutor, bossCount);
        this.determiner = determiner;
    }

    /**
     * Create a new instance using no {@link ThreadNameDeterminer}
     *
     * @param bossExecutor  the {@link Executor} to use for server the {@link NioServerBoss}
     * @param bossCount     the number of {@link NioServerBoss} instances this {@link NioServerBossPool} will hold
     */
    public NioServerBossPool(Executor bossExecutor, int bossCount) {
        this(bossExecutor, bossCount, null);
    }

    @Override
    protected NioServerBoss newBoss(Executor executor) {
        return new NioServerBoss(executor, determiner);
    }
}
