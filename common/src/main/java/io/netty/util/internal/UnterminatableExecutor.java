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
package io.netty.util.internal;

import java.util.concurrent.Executor;

/**
 * An implementation of {@link Executor} that may not be shut down or terminated
 */
public class UnterminatableExecutor implements Executor {

    /**
     * The {@link Executor} instance being used
     */
    private final Executor executor;

    /**
     * Creates a new {@link UnterminatableExecutor}
     * 
     * @param executor The initial {@link Executor} to wrap
     */
    public UnterminatableExecutor(Executor executor) {
        //See if the executor being provided is null
        if (executor == null) {
            //Alright, this doesn't look good!
            throw new NullPointerException("Executors provided to "
                    + "UnterminableExecutors must not be null");
        }
        //Set the executor
        this.executor = executor;
    }

    /**
     * Executes the specified command
     * 
     * @param command The command to execute
     */
    @Override
    public void execute(Runnable command) {
        executor.execute(command);
    }
}
