/*
 * Copyright 2018 The Netty Project
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

package io.netty.channel;

import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.StringUtil;

import java.lang.reflect.Constructor;

/**
 * A {@link ChannelFactory} that instantiates a new {@link ServerChannel} by invoking its default constructor
 * reflectively.
 */
public final class ReflectiveServerChannelFactory<T extends ServerChannel> implements ServerChannelFactory<T> {

    private final Constructor<? extends T> constructor;

    public ReflectiveServerChannelFactory(Class<? extends T> clazz) {
        ObjectUtil.checkNotNull(clazz, "clazz");
        try {
            this.constructor = clazz.getConstructor(EventLoop.class, EventLoopGroup.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Class " + StringUtil.simpleClassName(clazz) +
                    " does not have a public constructor that takes an EventLoop and EventLoopGroup instance", e);
        }
    }

    @Override
    public T newChannel(EventLoop eventLoop, EventLoopGroup childEventLoopGroup) {
        try {
            return constructor
                    .newInstance(eventLoop, childEventLoopGroup);
        } catch (Throwable t) {
            throw new ChannelException(
                    "Unable to create ServerChannel from class " + constructor.getDeclaringClass(), t);
        }
    }

    @Override
    public String toString() {
        return StringUtil.simpleClassName(ReflectiveServerChannelFactory.class) +
                '(' + StringUtil.simpleClassName(constructor.getDeclaringClass()) + ".class)";
    }
}
