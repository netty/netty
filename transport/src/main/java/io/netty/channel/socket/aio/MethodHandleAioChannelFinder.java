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
package io.netty.channel.socket.aio;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class MethodHandleAioChannelFinder implements AioChannelFinder {
    private static volatile Map<Class<?>, MethodHandle> handleCache = new HashMap<Class<?>, MethodHandle>();

    @Override
    public AbstractAioChannel findChannel(Runnable command) throws Exception {
        MethodHandle handle;
        for (;;) {
            handle = findHandle(command);
            if (handle == null) {
                return null;
            }
            Object next;
            try {
                next = handle.invokeExact();
            } catch (Throwable e) {
                e.printStackTrace();
                throw new Exception("Unable to invoke handle " + handle, e);
            }
            if (next instanceof AbstractAioChannel) {
                return (AbstractAioChannel) next;
            }
            command = (Runnable) next;
        }
    }

    private static MethodHandle findHandle(Object command) throws Exception {
        Map<Class<?>, MethodHandle> handleCache = MethodHandleAioChannelFinder.handleCache;
        Class<?> commandType = command.getClass();
        MethodHandle res = handleCache.get(commandType);
        if (res != null) {
            return res;
        }

        for (Field f: commandType.getDeclaredFields()) {
            if (f.getType() == Runnable.class) {
                return put(handleCache, commandType, f);
            }

            // Check against the actual class as this is what will be used by the jdk
            // if you use a final variable and pass it to a Runnable
            if (f.getType().isAssignableFrom(AbstractAioChannel.class)
                    || f.getType() == Object.class) {
                f.setAccessible(true);
                Object candidate = f.get(command);
                if (candidate instanceof AbstractAioChannel) {
                    return put(handleCache, commandType, f);
                }
            }
        }
        return null;
    }

    private static MethodHandle put(Map<Class<?>, MethodHandle> oldCache, Class<?> key, Field value) throws Exception {
        MethodHandle handle = MethodHandles.lookup().findGetter(key, value.getName(), value.getType());
        Map<Class<?>, MethodHandle> newCache = new HashMap<Class<?>, MethodHandle>(oldCache.size());
        newCache.putAll(oldCache);
        newCache.put(key, handle);
        handleCache = newCache;
        return handle;
    }
}
