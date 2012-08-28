package io.netty.channel.socket.aio;

import java.lang.reflect.Field;

import sun.misc.Unsafe;

@SuppressWarnings("restriction")
class UnsafeAioChannelFinder extends DefaultAioChannelFinder {
    private static final Unsafe UNSAFE = getUnsafe();

    @Override
    protected Object get(Field f, Object command) throws Exception {
        // using Unsafe to directly access the field. This should be
        // faster then "pure" reflection
        long offset = UNSAFE.objectFieldOffset(f);
        return UNSAFE.getObject(command, offset);
    }

    private static Unsafe getUnsafe() {
        try {
            Field singleoneInstanceField = Unsafe.class.getDeclaredField("theUnsafe");
            singleoneInstanceField.setAccessible(true);
            return (Unsafe) singleoneInstanceField.get(null);
        } catch (Throwable cause) {
            throw new RuntimeException("Error while obtaining sun.misc.Unsafe", cause);
        }
    }
}
