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

package io.netty.util.internal;

import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.concurrent.FastThreadLocalThread;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The internal data structure that stores the thread-local variables for Netty and all {@link FastThreadLocal}s.
 * Note that this class is for internal use only and is subject to change at any time.  Use {@link FastThreadLocal}
 * unless you know what you are doing.
 */
public final class InternalThreadLocalMap extends UnpaddedInternalThreadLocalMap {
    private static final ThreadLocal<InternalThreadLocalMap> slowThreadLocalMap =
            new ThreadLocal<InternalThreadLocalMap>();
    private static final AtomicInteger nextIndex = new AtomicInteger();
    // Internal use only.
    public static final int VARIABLES_TO_REMOVE_INDEX = nextVariableIndex();

    private static final int DEFAULT_ARRAY_LIST_INITIAL_CAPACITY = 8;
    private static final int ARRAY_LIST_CAPACITY_EXPAND_THRESHOLD = 1 << 30;
    // Reference: https://hg.openjdk.java.net/jdk8/jdk8/jdk/file/tip/src/share/classes/java/util/ArrayList.java#l229
    private static final int ARRAY_LIST_CAPACITY_MAX_SIZE = Integer.MAX_VALUE - 8;

    private static final int HANDLER_SHARABLE_CACHE_INITIAL_CAPACITY = 4;
    private static final int INDEXED_VARIABLE_TABLE_INITIAL_SIZE = 32;

    private static final int STRING_BUILDER_INITIAL_SIZE;
    private static final int STRING_BUILDER_MAX_SIZE;

    private static final InternalLogger logger;
    /** Internal use only. */
    public static final Object UNSET = new Object();

    /** Used by {@link FastThreadLocal} */
    private Object[] indexedVariables;

    // Core thread-locals
    private int futureListenerStackDepth;
    private int localChannelReaderStackDepth;
    private Map<Class<?>, Boolean> handlerSharableCache;
    private IntegerHolder counterHashCode;
    private ThreadLocalRandom random;
    private Map<Class<?>, TypeParameterMatcher> typeParameterMatcherGetCache;
    private Map<Class<?>, Map<String, TypeParameterMatcher>> typeParameterMatcherFindCache;

    // String-related thread-locals
    private StringBuilder stringBuilder;
    private Map<Charset, CharsetEncoder> charsetEncoderCache;
    private Map<Charset, CharsetDecoder> charsetDecoderCache;

    // ArrayList-related thread-locals
    private ArrayList<Object> arrayList;

    private BitSet cleanerFlags;

    /** @deprecated These padding fields will be removed in the future. */
    public long rp1, rp2, rp3, rp4, rp5, rp6, rp7, rp8;

    static {
        STRING_BUILDER_INITIAL_SIZE =
                SystemPropertyUtil.getInt("io.netty.threadLocalMap.stringBuilder.initialSize", 1024);
        STRING_BUILDER_MAX_SIZE =
                SystemPropertyUtil.getInt("io.netty.threadLocalMap.stringBuilder.maxSize", 1024 * 4);

        // Ensure the InternalLogger is initialized as last field in this class as InternalThreadLocalMap might be used
        // by the InternalLogger itself. For this its important that all the other static fields are correctly
        // initialized.
        //
        // See https://github.com/netty/netty/issues/12931.
        logger = InternalLoggerFactory.getInstance(InternalThreadLocalMap.class);
        logger.debug("-Dio.netty.threadLocalMap.stringBuilder.initialSize: {}", STRING_BUILDER_INITIAL_SIZE);
        logger.debug("-Dio.netty.threadLocalMap.stringBuilder.maxSize: {}", STRING_BUILDER_MAX_SIZE);
    }

    public static InternalThreadLocalMap getIfSet() {
        Thread thread = Thread.currentThread();
        if (thread instanceof FastThreadLocalThread) {
            return ((FastThreadLocalThread) thread).threadLocalMap();
        }
        return slowThreadLocalMap.get();
    }

    public static InternalThreadLocalMap get() {
        Thread thread = Thread.currentThread();
        if (thread instanceof FastThreadLocalThread) {
            return fastGet((FastThreadLocalThread) thread);
        } else {
            return slowGet();
        }
    }

    private static InternalThreadLocalMap fastGet(FastThreadLocalThread thread) {
        InternalThreadLocalMap threadLocalMap = thread.threadLocalMap();
        if (threadLocalMap == null) {
            thread.setThreadLocalMap(threadLocalMap = new InternalThreadLocalMap());
        }
        return threadLocalMap;
    }

    private static InternalThreadLocalMap slowGet() {
        InternalThreadLocalMap ret = slowThreadLocalMap.get();
        if (ret == null) {
            ret = new InternalThreadLocalMap();
            slowThreadLocalMap.set(ret);
        }
        return ret;
    }

    public static void remove() {
        Thread thread = Thread.currentThread();
        if (thread instanceof FastThreadLocalThread) {
            ((FastThreadLocalThread) thread).setThreadLocalMap(null);
        } else {
            slowThreadLocalMap.remove();
        }
    }

    public static void destroy() {
        slowThreadLocalMap.remove();
    }

    public static int nextVariableIndex() {
        int index = nextIndex.getAndIncrement();
        if (index >= ARRAY_LIST_CAPACITY_MAX_SIZE || index < 0) {
            nextIndex.set(ARRAY_LIST_CAPACITY_MAX_SIZE);
            throw new IllegalStateException("too many thread-local indexed variables");
        }
        return index;
    }

    public static int lastVariableIndex() {
        return nextIndex.get() - 1;
    }

    private InternalThreadLocalMap() {
        indexedVariables = newIndexedVariableTable();
    }

    private static Object[] newIndexedVariableTable() {
        Object[] array = new Object[INDEXED_VARIABLE_TABLE_INITIAL_SIZE];
        Arrays.fill(array, UNSET);
        return array;
    }

    public int size() {
        int count = 0;

        if (futureListenerStackDepth != 0) {
            count ++;
        }
        if (localChannelReaderStackDepth != 0) {
            count ++;
        }
        if (handlerSharableCache != null) {
            count ++;
        }
        if (counterHashCode != null) {
            count ++;
        }
        if (random != null) {
            count ++;
        }
        if (typeParameterMatcherGetCache != null) {
            count ++;
        }
        if (typeParameterMatcherFindCache != null) {
            count ++;
        }
        if (stringBuilder != null) {
            count ++;
        }
        if (charsetEncoderCache != null) {
            count ++;
        }
        if (charsetDecoderCache != null) {
            count ++;
        }
        if (arrayList != null) {
            count ++;
        }

        Object v = indexedVariable(VARIABLES_TO_REMOVE_INDEX);
        if (v != null && v != InternalThreadLocalMap.UNSET) {
            @SuppressWarnings("unchecked")
            Set<FastThreadLocal<?>> variablesToRemove = (Set<FastThreadLocal<?>>) v;
            count += variablesToRemove.size();
        }

        return count;
    }

    public StringBuilder stringBuilder() {
        StringBuilder sb = stringBuilder;
        if (sb == null) {
            return stringBuilder = new StringBuilder(STRING_BUILDER_INITIAL_SIZE);
        }
        if (sb.capacity() > STRING_BUILDER_MAX_SIZE) {
            sb.setLength(STRING_BUILDER_INITIAL_SIZE);
            sb.trimToSize();
        }
        sb.setLength(0);
        return sb;
    }

    public Map<Charset, CharsetEncoder> charsetEncoderCache() {
        Map<Charset, CharsetEncoder> cache = charsetEncoderCache;
        if (cache == null) {
            charsetEncoderCache = cache = new IdentityHashMap<Charset, CharsetEncoder>();
        }
        return cache;
    }

    public Map<Charset, CharsetDecoder> charsetDecoderCache() {
        Map<Charset, CharsetDecoder> cache = charsetDecoderCache;
        if (cache == null) {
            charsetDecoderCache = cache = new IdentityHashMap<Charset, CharsetDecoder>();
        }
        return cache;
    }

    public <E> ArrayList<E> arrayList() {
        return arrayList(DEFAULT_ARRAY_LIST_INITIAL_CAPACITY);
    }

    @SuppressWarnings("unchecked")
    public <E> ArrayList<E> arrayList(int minCapacity) {
        ArrayList<E> list = (ArrayList<E>) arrayList;
        if (list == null) {
            arrayList = new ArrayList<Object>(minCapacity);
            return (ArrayList<E>) arrayList;
        }
        list.clear();
        list.ensureCapacity(minCapacity);
        return list;
    }

    public int futureListenerStackDepth() {
        return futureListenerStackDepth;
    }

    public void setFutureListenerStackDepth(int futureListenerStackDepth) {
        this.futureListenerStackDepth = futureListenerStackDepth;
    }

    public ThreadLocalRandom random() {
        ThreadLocalRandom r = random;
        if (r == null) {
            random = r = new ThreadLocalRandom();
        }
        return r;
    }

    public Map<Class<?>, TypeParameterMatcher> typeParameterMatcherGetCache() {
        Map<Class<?>, TypeParameterMatcher> cache = typeParameterMatcherGetCache;
        if (cache == null) {
            typeParameterMatcherGetCache = cache = new IdentityHashMap<Class<?>, TypeParameterMatcher>();
        }
        return cache;
    }

    public Map<Class<?>, Map<String, TypeParameterMatcher>> typeParameterMatcherFindCache() {
        Map<Class<?>, Map<String, TypeParameterMatcher>> cache = typeParameterMatcherFindCache;
        if (cache == null) {
            typeParameterMatcherFindCache = cache = new IdentityHashMap<Class<?>, Map<String, TypeParameterMatcher>>();
        }
        return cache;
    }

    @Deprecated
    public IntegerHolder counterHashCode() {
        return counterHashCode;
    }

    @Deprecated
    public void setCounterHashCode(IntegerHolder counterHashCode) {
        this.counterHashCode = counterHashCode;
    }

    public Map<Class<?>, Boolean> handlerSharableCache() {
        Map<Class<?>, Boolean> cache = handlerSharableCache;
        if (cache == null) {
            // Start with small capacity to keep memory overhead as low as possible.
            handlerSharableCache = cache = new WeakHashMap<Class<?>, Boolean>(HANDLER_SHARABLE_CACHE_INITIAL_CAPACITY);
        }
        return cache;
    }

    public int localChannelReaderStackDepth() {
        return localChannelReaderStackDepth;
    }

    public void setLocalChannelReaderStackDepth(int localChannelReaderStackDepth) {
        this.localChannelReaderStackDepth = localChannelReaderStackDepth;
    }

    public Object indexedVariable(int index) {
        Object[] lookup = indexedVariables;
        return index < lookup.length? lookup[index] : UNSET;
    }

    /**
     * @return {@code true} if and only if a new thread-local variable has been created
     */
    public boolean setIndexedVariable(int index, Object value) {
        Object[] lookup = indexedVariables;
        if (index < lookup.length) {
            Object oldValue = lookup[index];
            lookup[index] = value;
            return oldValue == UNSET;
        } else {
            expandIndexedVariableTableAndSet(index, value);
            return true;
        }
    }

    private void expandIndexedVariableTableAndSet(int index, Object value) {
        Object[] oldArray = indexedVariables;
        final int oldCapacity = oldArray.length;
        int newCapacity;
        if (index < ARRAY_LIST_CAPACITY_EXPAND_THRESHOLD) {
            newCapacity = index;
            newCapacity |= newCapacity >>>  1;
            newCapacity |= newCapacity >>>  2;
            newCapacity |= newCapacity >>>  4;
            newCapacity |= newCapacity >>>  8;
            newCapacity |= newCapacity >>> 16;
            newCapacity ++;
        } else {
            newCapacity = ARRAY_LIST_CAPACITY_MAX_SIZE;
        }

        Object[] newArray = Arrays.copyOf(oldArray, newCapacity);
        Arrays.fill(newArray, oldCapacity, newArray.length, UNSET);
        newArray[index] = value;
        indexedVariables = newArray;
    }

    public Object removeIndexedVariable(int index) {
        Object[] lookup = indexedVariables;
        if (index < lookup.length) {
            Object v = lookup[index];
            lookup[index] = UNSET;
            return v;
        } else {
            return UNSET;
        }
    }

    public boolean isIndexedVariableSet(int index) {
        Object[] lookup = indexedVariables;
        return index < lookup.length && lookup[index] != UNSET;
    }

    public boolean isCleanerFlagSet(int index) {
        return cleanerFlags != null && cleanerFlags.get(index);
    }

    public void setCleanerFlag(int index) {
        if (cleanerFlags == null) {
            cleanerFlags = new BitSet();
        }
        cleanerFlags.set(index);
    }
}
