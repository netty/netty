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

package io.netty.channel;

import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static io.netty.channel.EventLoopGroups.fqcn;
import static io.netty.channel.EventLoopGroups.ucFirst;

/**
 * {@link Channel} helper utilities.
 */
public final class Channels {
    private static final Map<Class<? extends EventLoopGroup>, Class<? extends Channel>> datagram = createDatagramMap();
    private static final Map<Class<? extends EventLoopGroup>, Class<? extends Channel>> client = createClientMap();
    private static final Map<Class<? extends EventLoopGroup>, Class<? extends Channel>> server = createServerMap();

    private Channels() {
    }

    /**
     * Returns {@link SocketChannel} class that is compatible with specified event loop group.
     *
     * @param group event loop group.
     * @return channel class if found, otherwise <code>null</code>.
     * @see io.netty.channel.socket.SocketChannel
     */
    public static Class<SocketChannel> socketChannelType(EventLoopGroup group) {
        return (group == null) ? null : socketChannelType(group.getClass());
    }

    /**
     * Returns {@link io.netty.channel.socket.SocketChannel} class that is compatible with specified event loop group
     * class.
     *
     * @param groupClass event loop group class.
     * @return channel class if found, otherwise <code>null</code>.
     * @see io.netty.channel.socket.SocketChannel
     */
    public static Class<SocketChannel> socketChannelType(Class<? extends EventLoopGroup> groupClass) {
        return (Class<SocketChannel>) getFromMap(client, groupClass);
    }

    /**
     * Returns {@link io.netty.channel.socket.ServerSocketChannel} class that is compatible with specified event loop
     * group.
     *
     * @param group event loop group.
     * @return channel class if found, otherwise <code>null</code>.
     * @see io.netty.channel.socket.ServerSocketChannel
     */
    public static Class<ServerSocketChannel> serverSocketChannelType(EventLoopGroup group) {
        return (group == null) ? null : serverSocketChannelType(group.getClass());
    }

    /**
     * Returns {@link io.netty.channel.socket.ServerSocketChannel} class that is compatible with specified event loop
     * group class.
     *
     * @param groupClass event loop group class.
     * @return channel class if found, otherwise <code>null</code>.
     * @see io.netty.channel.socket.SocketChannel
     */
    public static Class<ServerSocketChannel> serverSocketChannelType(Class<? extends EventLoopGroup> groupClass) {
        return (Class<ServerSocketChannel>) getFromMap(server, groupClass);
    }

    /**
     * Returns {@link io.netty.channel.socket.DatagramChannel} class that is compatible with specified event loop group.
     *
     * @param group event loop group.
     * @return channel class if found, otherwise <code>null</code>.
     * @see io.netty.channel.socket.DatagramChannel
     */
    public static Class<DatagramChannel> datagramChannelType(EventLoopGroup group) {
        return (group == null) ? null : datagramChannelType(group.getClass());
    }

    /**
     * Returns {@link io.netty.channel.socket.DatagramChannel} class that is compatible with specified event loop group
     * class.
     *
     * @param groupClass event loop group class.
     * @return channel class if found, otherwise <code>null</code>.
     * @see io.netty.channel.socket.DatagramChannel
     */
    public static Class<DatagramChannel> datagramChannelType(Class<? extends EventLoopGroup> groupClass) {
        return (Class<DatagramChannel>) getFromMap(datagram, groupClass);
    }

    private static Class<? extends Channel> getFromMap(
            Map<Class<? extends EventLoopGroup>, Class<? extends Channel>> map,
            Class<? extends EventLoopGroup> groupClass) {

        if (map == null || groupClass == null) {
            return null;
        }

        Class<? extends Channel> clazz = map.get(groupClass);
        if (clazz == null) {
            for (Map.Entry<Class<? extends EventLoopGroup>, Class<? extends Channel>> e : map.entrySet()) {
                if (e.getKey().isAssignableFrom(groupClass)) {
                    clazz = e.getValue();
                    break;
                }
            }
        }

        return clazz;
    }

    private static Map<Class<? extends EventLoopGroup>, Class<? extends Channel>> createMap() {
        return new HashMap<Class<? extends EventLoopGroup>, Class<? extends Channel>>(4);
    }

    private static Map<Class<? extends EventLoopGroup>, Class<? extends Channel>> createDatagramMap() {
        Map<Class<? extends EventLoopGroup>, Class<? extends Channel>> res = createMap();

        for (String impl : EventLoopGroups.standardImplementations()) {
            Class<? extends EventLoopGroup> elgClass = loadStandardEventLoopGroupClass(impl);
            Class<? extends Channel> channelClass = loadStandardChannelClass(impl, "DatagramChannel");
            if (elgClass == null || channelClass == null) {
                continue;
            }
            res.put(elgClass, channelClass);
        }

        addNativeImplementations(res, "DatagramChannel");

        return Collections.unmodifiableMap(res);
    }

    private static Map<Class<? extends EventLoopGroup>, Class<? extends Channel>> createClientMap() {
        Map<Class<? extends EventLoopGroup>, Class<? extends Channel>> res = createMap();

        for (String impl : EventLoopGroups.standardImplementations()) {
            Class<? extends EventLoopGroup> elgClass = loadStandardEventLoopGroupClass(impl);
            Class<? extends Channel> channelClass = loadStandardChannelClass(impl, "SocketChannel");
            if (elgClass == null || channelClass == null) {
                continue;
            }
            res.put(elgClass, channelClass);
        }

        addNativeImplementations(res, "SocketChannel");
        return Collections.unmodifiableMap(res);
    }

    private static Map<Class<? extends EventLoopGroup>, Class<? extends Channel>> createServerMap() {
        Map<Class<? extends EventLoopGroup>, Class<? extends Channel>> res = createMap();

        for (String impl : EventLoopGroups.standardImplementations()) {
            Class<? extends EventLoopGroup> elgClass = loadStandardEventLoopGroupClass(impl);
            Class<? extends Channel> channelClass = loadStandardChannelClass(impl, "ServerSocketChannel");
            if (elgClass == null || channelClass == null) {
                continue;
            }
            res.put(elgClass, channelClass);
        }

        addNativeImplementations(res, "ServerSocketChannel");
        return Collections.unmodifiableMap(res);
    }

    private static void addNativeImplementations(Map<Class<? extends EventLoopGroup>, Class<? extends Channel>> map,
                                                 String classSuffix) {
        for (String implementation : EventLoopGroups.nativeImplementations()) {
            String pkg = EventLoopGroups.CHANNEL_PACKAGE + "." + implementation;

            // load event loop group class name
            String eventLoopGroupClassName = pkg + "." + ucFirst(implementation) + "EventLoopGroup";
            Class<? extends EventLoopGroup> elgClass = EventLoopGroups.loadEventLoopGroupClass(eventLoopGroupClassName);
            if (elgClass == null) {
                continue;
            }

            // load channel class name.
            String channelClassName = pkg + "." + ucFirst(implementation) + classSuffix;
            Class<? extends Channel> channelClass = loadChannelClass(channelClassName);
            if (channelClass == null) {
                continue;
            }

            map.put(elgClass, channelClass);
        }
    }

    private static Class<? extends Channel> loadStandardChannelClass(String impl, String className) {
        return loadChannelClass(fqcn("socket." + impl + "." + ucFirst(impl) + className));
    }

    private static Class<? extends EventLoopGroup> loadStandardEventLoopGroupClass(String impl) {
        return EventLoopGroups.loadEventLoopGroupClass(fqcn(impl + "." + ucFirst(impl) + "EventLoopGroup"));
    }

    private static Class<? extends Channel> loadChannelClass(String fqcn) {
        return EventLoopGroups.loadClass(fqcn, Channel.class);
    }
}
