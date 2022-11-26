/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.channel;

import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.StringUtil;

import java.lang.reflect.Constructor;

/**
 * A {@link ChannelFactory} that instantiates a new {@link Channel} by invoking its default constructor reflectively.
 *
 * 工厂模式应用
 */
public class ReflectiveChannelFactory<T extends Channel> implements ChannelFactory<T> {

    private final Constructor<? extends T> constructor;

    /**
     * 通过 channel(NioServerSocketChannel.class) 配置 Channel 的类型，工厂类 ReflectiveChannelFactory 是在该过程中被创建的。
     * 从 constructor.newInstance() 我们可以看出，ReflectiveChannelFactory 通过反射创建出 NioServerSocketChannel 对象，所以我们重点需要关注 NioServerSocketChannel 的构造函数。
     *
     * @param clazz
     */
    public ReflectiveChannelFactory(Class<? extends T> clazz) {
        ObjectUtil.checkNotNull(clazz, "clazz");
        try {
            this.constructor = clazz.getConstructor();
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Class " + StringUtil.simpleClassName(clazz) +
                    " does not have a public non-arg constructor", e);
        }
    }

    @Override
    public T newChannel() {
        try {
            // 反射创建对象，使用的是 NioServerSocketChannel 默认构造器
            /**
             * 过程：
             * 1. 通过 NIO 的 SelectorProvider 的 openServerSocketChannel 方法得到 JDK 的 channel，目的是让 Netty 包装 JDK 的 channel；
             * 2. 创建一个唯一的 ChannelId，创建了一个 NioMessageUnsafe，用于操作消息，创建了一个 DefaultChannelPipeline 管道，是个双向链表结构，用于过滤所有的进出的消息；
             * 3. 创建了一个 NioServerSocketChannelConfig 对象，用于对外展示一些配置；
             */
            return constructor.newInstance();
        } catch (Throwable t) {
            throw new ChannelException("Unable to create Channel from class " + constructor.getDeclaringClass(), t);
        }
    }

    @Override
    public String toString() {
        return StringUtil.simpleClassName(ReflectiveChannelFactory.class) +
                '(' + StringUtil.simpleClassName(constructor.getDeclaringClass()) + ".class)";
    }
}
