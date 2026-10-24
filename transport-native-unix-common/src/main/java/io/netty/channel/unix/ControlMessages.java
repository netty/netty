/*
 * Copyright 2026 The Netty Project
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
package io.netty.channel.unix;

import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.ObjectUtil;

import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * An immutable collection of {@link ControlMessage}s and their values, which is carried by a
 * {@link ControlMessageDatagramPacket}.
 * <p>
 * As instances can't be modified these can be shared, for example to send multiple datagrams with the same control
 * messages (as long as the values themselves are not modified, which is only possible for the
 * {@link java.nio.ByteBuffer} of a {@link RawControlMessage}).
 * <pre>{@code
 * ControlMessages controlMessages = ControlMessages.builder()
 *         .set(ControlMessage.UDP_SEGMENT, 1200)
 *         .set(ControlMessage.IP_TOS, 0x02)
 *         .build();
 * }</pre>
 */
public final class ControlMessages {

    private static final ControlMessage<?>[] EMPTY_TYPES = new ControlMessage<?>[0];

    /**
     * {@link ControlMessages} that contain no {@link ControlMessage}s.
     */
    public static final ControlMessages EMPTY = new ControlMessages(EMPTY_TYPES, EmptyArrays.EMPTY_OBJECTS);

    // These are never modified after construction.
    private final ControlMessage<?>[] types;
    private final Object[] values;
    private Set<ControlMessage<?>> typeSet;

    // Package-private as it is also used when decoding received control messages, in which case the values are
    // already valid and the types are unique.
    ControlMessages(ControlMessage<?>[] types, Object[] values) {
        assert types.length == values.length;
        this.types = types;
        this.values = values;
    }

    /**
     * Returns {@link ControlMessages} that contain the given {@link ControlMessage} with the given value.
     */
    public static <T> ControlMessages of(ControlMessage<T> type, T value) {
        validate(type, value);
        return new ControlMessages(new ControlMessage<?>[] { type }, new Object[] { value });
    }

    /**
     * Returns {@link ControlMessages} that contain the given {@link ControlMessage}s with the given values.
     */
    public static <T1, T2> ControlMessages of(ControlMessage<T1> type1, T1 value1,
                                              ControlMessage<T2> type2, T2 value2) {
        return builder().set(type1, value1).set(type2, value2).build();
    }

    /**
     * Returns a new {@link Builder} that can be used to build {@link ControlMessages}.
     */
    public static Builder builder() {
        return new Builder(EMPTY_TYPES, EmptyArrays.EMPTY_OBJECTS);
    }

    /**
     * Returns a new {@link Builder} which contains all the {@link ControlMessage}s and their values of this instance.
     */
    public Builder toBuilder() {
        return new Builder(types, values);
    }

    /**
     * Returns the value of the given {@link ControlMessage} or {@code null} if it is not contained.
     */
    @SuppressWarnings("unchecked")
    public <T> T get(ControlMessage<T> type) {
        int index = indexOf(types, types.length, ObjectUtil.checkNotNull(type, "type"));
        return index == -1 ? null : (T) values[index];
    }

    /**
     * Returns {@code true} if the given {@link ControlMessage} is contained.
     */
    public boolean contains(ControlMessage<?> type) {
        return indexOf(types, types.length, ObjectUtil.checkNotNull(type, "type")) != -1;
    }

    /**
     * Returns an unmodifiable {@link Set} that contains all the {@link ControlMessage}s, in the order in which these
     * were added. The values can be retrieved via {@link #get(ControlMessage)}.
     */
    public Set<ControlMessage<?>> types() {
        Set<ControlMessage<?>> typeSet = this.typeSet;
        if (typeSet == null) {
            typeSet = new TypeSet(types);
            this.typeSet = typeSet;
        }
        return typeSet;
    }

    /**
     * Returns the number of {@link ControlMessage}s.
     */
    public int size() {
        return types.length;
    }

    /**
     * Returns {@code true} if no {@link ControlMessage}s are contained.
     */
    public boolean isEmpty() {
        return types.length == 0;
    }

    ControlMessage<?> type(int index) {
        return types[index];
    }

    Object value(int index) {
        return values[index];
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ControlMessages)) {
            return false;
        }
        ControlMessages that = (ControlMessages) o;
        if (types.length != that.types.length) {
            return false;
        }
        // The order does not matter.
        for (int i = 0; i < types.length; i++) {
            int index = indexOf(that.types, that.types.length, types[i]);
            if (index == -1 || !values[i].equals(that.values[index])) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        // The order does not matter.
        int hashCode = 0;
        for (int i = 0; i < types.length; i++) {
            hashCode += types[i].hashCode() ^ values[i].hashCode();
        }
        return hashCode;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder().append('[');
        for (int i = 0; i < types.length; i++) {
            if (i != 0) {
                sb.append(", ");
            }
            sb.append(types[i]).append('=').append(values[i]);
        }
        return sb.append(']').toString();
    }

    private static int indexOf(ControlMessage<?>[] types, int size, ControlMessage<?> type) {
        for (int i = 0; i < size; i++) {
            if (types[i] == type) {
                return i;
            }
        }
        return -1;
    }

    private static <T> void validate(ControlMessage<T> type, T value) {
        ObjectUtil.checkNotNull(type, "type").validate(value);
    }

    /**
     * A builder for {@link ControlMessages}.
     */
    public static final class Builder {
        private ControlMessage<?>[] types;
        private Object[] values;
        private int size;

        private Builder(ControlMessage<?>[] types, Object[] values) {
            this.types = types.length == 0 ? new ControlMessage<?>[4] : types.clone();
            this.values = values.length == 0 ? new Object[4] : values.clone();
            this.size = types.length;
        }

        /**
         * Set the value of the given {@link ControlMessage}, replacing the value that was set before (if any).
         */
        public <T> Builder set(ControlMessage<T> type, T value) {
            validate(type, value);
            int index = indexOf(types, size, type);
            if (index == -1) {
                if (size == types.length) {
                    types = Arrays.copyOf(types, size << 1);
                    values = Arrays.copyOf(values, size << 1);
                }
                index = size++;
                types[index] = type;
            }
            values[index] = value;
            return this;
        }

        /**
         * Remove the given {@link ControlMessage} (if it was set before).
         */
        public Builder remove(ControlMessage<?> type) {
            int index = indexOf(types, size, ObjectUtil.checkNotNull(type, "type"));
            if (index != -1) {
                int moved = size - index - 1;
                System.arraycopy(types, index + 1, types, index, moved);
                System.arraycopy(values, index + 1, values, index, moved);
                size--;
                types[size] = null;
                values[size] = null;
            }
            return this;
        }

        /**
         * Returns new {@link ControlMessages} that contain all the {@link ControlMessage}s that were set.
         */
        public ControlMessages build() {
            if (size == 0) {
                return EMPTY;
            }
            return new ControlMessages(Arrays.copyOf(types, size), Arrays.copyOf(values, size));
        }
    }

    private static final class TypeSet extends AbstractSet<ControlMessage<?>> {
        private final ControlMessage<?>[] types;

        TypeSet(ControlMessage<?>[] types) {
            this.types = types;
        }

        @Override
        public Iterator<ControlMessage<?>> iterator() {
            return new Iterator<ControlMessage<?>>() {
                private int index;

                @Override
                public boolean hasNext() {
                    return index < types.length;
                }

                @Override
                public ControlMessage<?> next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }
                    return types[index++];
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException("remove");
                }
            };
        }

        @Override
        public boolean contains(Object o) {
            return o instanceof ControlMessage && indexOf(types, types.length, (ControlMessage<?>) o) != -1;
        }

        @Override
        public int size() {
            return types.length;
        }
    }
}
