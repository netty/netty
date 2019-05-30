/*
 * Copyright 2016 The Netty Project
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
package io.netty.util.concurrent;

import io.netty.util.internal.UnstableApi;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Default implementation which uses simple round-robin to choose next {@link EventExecutor}.
 */
@UnstableApi
public final class DefaultEventExecutorChooserFactory implements EventExecutorChooserFactory {

    /**
     * 使用工厂模式 单例
     */
    public static final DefaultEventExecutorChooserFactory INSTANCE = new DefaultEventExecutorChooserFactory();

    private DefaultEventExecutorChooserFactory() {
    }

    /**
     * 功能描述: <br>
     * 〈判断EventExecutor数组大小是否是2的幂次方〉
     *
     * @param:[executors]
     * @return:io.netty.util.concurrent.EventExecutorChooserFactory.EventExecutorChooser
     * @since: 1.0.0
     * @Author:s·D·bs
     * @Date: 2019/5/30 22:46
     */
    @SuppressWarnings("unchecked")
    @Override
    public EventExecutorChooser newChooser(EventExecutor[] executors) {

        if (isPowerOfTwo(executors.length)) {
            return new PowerOfTwoEventExecutorChooser(executors);
        } else {
            return new GenericEventExecutorChooser(executors);
        }
    }

    /**
     * 功能描述: <br>
     * 〈是否为 2 的幂次方〉
     * 8:1000  1*2^3+0*2^2 ..
     * -8:0111 就是8的补码（每位对应的数相反）
     * 那么只要是2的幂次方都是1开头
     * 8 & -8 = 1000 & 0001 =1000 取前真
     *
     * @param:val
     * @return:boolean
     * @since: 1.0.0
     * @Author:s·D·bs
     * @Date: 2019/5/30 22:38
     */
    private static boolean isPowerOfTwo(int val) {
        return (val & -val) == val;
    }


    private static final class PowerOfTwoEventExecutorChooser implements EventExecutorChooser {
        private final AtomicInteger idx = new AtomicInteger();
        private final EventExecutor[] executors;

        PowerOfTwoEventExecutorChooser(EventExecutor[] executors) {
            this.executors = executors;
        }

        /**
         * 减号的操作运算符优先级高于&一元运算符
         * A & B 永远小于B
         * 还保证有顺序
         *
         * @return
         */
        @Override
        public EventExecutor next() {
            return executors[idx.getAndIncrement() & executors.length - 1];
        }
    }

    private static final class GenericEventExecutorChooser implements EventExecutorChooser {
        /**
         * 自增序列
         */
        private final AtomicInteger idx = new AtomicInteger();
        /**
         * EventExecutor 数组
         */
        private final EventExecutor[] executors;

        GenericEventExecutorChooser(EventExecutor[] executors) {
            this.executors = executors;
        }

        /**
         * 可以保证自增，永远不会超过executors.length
         *
         * @return
         */
        @Override
        public EventExecutor next() {
            return executors[Math.abs(idx.getAndIncrement() % executors.length)];
        }
    }
}
