/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package net.gleamynode.netty.handler.execution;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import net.gleamynode.netty.buffer.ChannelBuffer;
import net.gleamynode.netty.channel.MessageEvent;

public class DefaultObjectSizeEstimator implements ObjectSizeEstimator {

    private final ConcurrentMap<Class<?>, Integer> class2size =
        new ConcurrentHashMap<Class<?>, Integer>();

    public DefaultObjectSizeEstimator() {
        class2size.put(boolean.class, 4); // Probably an integer.
        class2size.put(byte.class, 1);
        class2size.put(char.class, 2);
        class2size.put(int.class, 4);
        class2size.put(short.class, 2);
        class2size.put(long.class, 8);
        class2size.put(float.class, 4);
        class2size.put(double.class, 8);
        class2size.put(void.class, 0);
    }

    public int estimateSize(Object o) {
        if (o == null) {
            return 8;
        }

        int answer = 8 + estimateSize(o.getClass(), null);

        if (o instanceof ChannelEventRunnable) {
            answer += estimateSize(((ChannelEventRunnable) o).getEvent());
        } else if (o instanceof MessageEvent) {
            answer += estimateSize(((MessageEvent) o).getMessage());
        } else if (o instanceof ChannelBuffer) {
            answer += ((ChannelBuffer) o).capacity();
        } else if (o instanceof byte[]) {
            answer += ((byte[]) o).length;
        } else if (o instanceof ByteBuffer) {
            answer += ((ByteBuffer) o).remaining();
        } else if (o instanceof CharSequence) {
            answer += ((CharSequence) o).length() << 1;
        } else if (o instanceof Iterable) {
            for (Object m : (Iterable<?>) o) {
                answer += estimateSize(m);
            }
        }

        return align(answer);
    }

    private int estimateSize(Class<?> clazz, Set<Class<?>> visitedClasses) {
        Integer objectSize = class2size.get(clazz);
        if (objectSize != null) {
            return objectSize;
        }

        if (visitedClasses != null) {
            if (visitedClasses.contains(clazz)) {
                return 0;
            }
        } else {
            visitedClasses = new HashSet<Class<?>>();
        }

        visitedClasses.add(clazz);

        int answer = 8; // Basic overhead.
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            Field[] fields = c.getDeclaredFields();
            for (Field f : fields) {
                if ((f.getModifiers() & Modifier.STATIC) != 0) {
                    // Ignore static fields.
                    continue;
                }

                answer += estimateSize(f.getType(), visitedClasses);
            }
        }

        visitedClasses.remove(clazz);

        // Some alignment.
        answer = align(answer);

        // Put the final answer.
        class2size.putIfAbsent(clazz, answer);
        return answer;
    }

    private static int align(int size) {
        if (size % 8 != 0) {
            size /= 8;
            size ++;
            size *= 8;
        }
        return size;
    }
}
