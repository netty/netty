/*
 * Copyright 2019 The Netty Project
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
package io.netty.bootstrap;

import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.lang.reflect.Constructor;

public enum ChannelSystem {

    NIO(NioSocketChannel.class, NioServerSocketChannel.class) {
        @Override
        public EventLoopGroup newLoopGroup() {
            return new NioEventLoopGroup();
        }

        @Override
        public EventLoopGroup newLoopGroup(int nThreads) {
            return new NioEventLoopGroup(nThreads);
        }

        @Override
        public boolean isAvailable() {
            return true;
        }
    },
    EPOLL(null, null) {
        Class eventLoop;
        Constructor<EventLoopGroup> threadLoopConstructor;
        {
            try {
                eventLoop = Class.forName("io.netty.channel.epoll.EpollEventLoopGroup");
                threadLoopConstructor = eventLoop.getConstructor(int.class);
            } catch (ClassNotFoundException ignored) {

            } catch (NoSuchMethodException ignored) {

            }
        }
        @Override
        public EventLoopGroup newLoopGroup() {
            try {
                return (EventLoopGroup) eventLoop.newInstance();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public EventLoopGroup newLoopGroup(int nThreads) {
            try {
                return threadLoopConstructor.newInstance(nThreads);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public boolean isAvailable() {
            try {
                Class epoll = Class.forName("io.netty.channel.epoll.Epoll");
                return (Boolean) epoll.getDeclaredMethod("isAvailable").invoke(null);
            } catch (Exception e) {
                return false;
            }
        }
    };

    static {
        try {
            EPOLL.channelClass = (Class<? extends Channel>) Class.forName("io.netty.channel.epoll.EpollSocketChannel");
            EPOLL.serverChannelClass = (Class<? extends ServerChannel>) Class.forName("io.netty.channel.epoll.EpollServerSocketChannel");
        } catch (Exception ignored) {}
    }

    private Class<? extends Channel> channelClass;
    private Class<? extends ServerChannel> serverChannelClass;

    ChannelSystem(Class<? extends Channel> channelClass, Class<? extends ServerChannel> serverChannelClass) {
        this.channelClass = channelClass;
        this.serverChannelClass = serverChannelClass;
    }

    public abstract EventLoopGroup newLoopGroup();
    public abstract EventLoopGroup newLoopGroup(int nThreads);
    public abstract boolean isAvailable();

    public Class<? extends Channel> getChannelClass() {
        return channelClass;
    }

    public Class<? extends ServerChannel> getServerChannelClass() {
        return serverChannelClass;
    }

    private static ChannelSystem optimal;

    public static ChannelSystem getOptimal() {
        if (optimal == null) {
            optimal = EPOLL.isAvailable() ? EPOLL : NIO;
        }
        return optimal;
    }

}
