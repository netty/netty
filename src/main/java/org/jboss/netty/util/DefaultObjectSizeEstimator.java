/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.util;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.util.internal.ConcurrentIdentityWeakKeyHashMap;

/**
 * The default {@link ObjectSizeEstimator} implementation for general purpose.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 *
 */
public class DefaultObjectSizeEstimator implements ObjectSizeEstimator {

    private final ConcurrentMap<Class<?>, Integer> class2size =
        new ConcurrentIdentityWeakKeyHashMap<Class<?>, Integer>();

    /**
     * Creates a new instance.
     */
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

        if (o instanceof EstimatableObjectWrapper) {
            answer += estimateSize(((EstimatableObjectWrapper) o).unwrap());
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
        } else if (o instanceof Iterable<?>) {
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
        int r = size % 8;
        if (r != 0) {
            size += 8 - r;
        }
        return size;
    }
}
