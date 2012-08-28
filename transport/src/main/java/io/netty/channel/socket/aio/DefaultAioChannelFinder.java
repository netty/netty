package io.netty.channel.socket.aio;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

class DefaultAioChannelFinder implements AioChannelFinder {
    private static final ConcurrentMap<Class<?>, Field[]> fieldCache = new ConcurrentHashMap<Class<?>, Field[]>();
    private static final Field[] FAILURE = new Field[0];

    @Override
    public AbstractAioChannel findChannel(Runnable command) throws Exception {
        Class<?> commandType = command.getClass();
        Field[] fields = fieldCache.get(commandType);
        if (fields == null) {
            try {
                fields = findFieldSequence(command, new ArrayDeque<Field>(2));
            } catch (Throwable t) {
                // Failed to get the field list
            }

            if (fields == null) {
                fields = FAILURE;
            }

            fieldCache.put(commandType, fields); // No need to use putIfAbsent()
        }

        if (fields == FAILURE) {
            return null;
        }

        final int lastIndex = fields.length - 1;
        for (int i = 0; i < lastIndex; i ++) {
            command = (Runnable) get(fields[i], command);
        }

        return (AbstractAioChannel) get(fields[lastIndex], command);
    }

    private Field[] findFieldSequence(Runnable command, Deque<Field> fields) throws Exception {
        Class<?> commandType = command.getClass();
        for (Field f: commandType.getDeclaredFields()) {
            if (f.getType() == Runnable.class) {
                f.setAccessible(true);
                fields.addLast(f);
                try {
                    Field[] ret = findFieldSequence((Runnable) get(f, command), fields);
                    if (ret != null) {
                        return ret;
                    }
                } finally {
                    fields.removeLast();
                }
            }

            if (f.getType() == Object.class) {
                f.setAccessible(true);
                fields.addLast(f);
                try {
                    Object candidate = get(f, command);
                    if (candidate instanceof AbstractAioChannel) {
                        return fields.toArray(new Field[fields.size()]);
                    }
                } finally {
                    fields.removeLast();
                }
            }
        }

        return null;
    }

    protected Object get(Field f, Object command) throws Exception {
        return f.get(command);
    }
}
