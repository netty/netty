package io.netty.channel.socket.aio;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

class DefaultAioChannelFinder implements AioChannelFinder {
    private static volatile Map<Class<?>, Field> fieldCache = new HashMap<Class<?>, Field>();

    @Override
    public AbstractAioChannel findChannel(Runnable command) throws Exception {
        Field f;
        for (;;) {
            f = findField(command);
            if (f == null) {
                return null;
            }
            Object next = f.get(command);
            if (next instanceof AbstractAioChannel) {
                return (AbstractAioChannel) next;
            }
            command = (Runnable) next;
        }
    }

    private static Field findField(Object command) throws Exception {
        Map<Class<?>, Field> fieldCache = DefaultAioChannelFinder.fieldCache;
        Class<?> commandType = command.getClass();
        Field res = fieldCache.get(commandType);
        if (res != null) {
            return res;
        }

        for (Field f: commandType.getDeclaredFields()) {
            if (f.getType() == Runnable.class) {
                f.setAccessible(true);
                put(fieldCache, commandType, f);
                return f;
            }

            if (f.getType() == Object.class) {
                f.setAccessible(true);
                Object candidate = f.get(command);
                if (candidate instanceof AbstractAioChannel) {
                    put(fieldCache, commandType, f);
                    return f;
                }
            }
        }
        return null;
    }

    private static void put(Map<Class<?>, Field> oldCache, Class<?> key, Field value) {
        Map<Class<?>, Field> newCache = new HashMap<Class<?>, Field>(oldCache.size());
        newCache.putAll(oldCache);
        newCache.put(key, value);
        fieldCache = newCache;
    }
}
