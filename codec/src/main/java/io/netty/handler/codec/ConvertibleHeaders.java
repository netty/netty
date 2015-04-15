/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Extension to the {@link Headers} interface to provide methods which convert the
 * native {@code UnconvertedType} to the not-native {@code ConvertedType}
 */
public interface ConvertibleHeaders<UnconvertedType, ConvertedType> extends Headers<UnconvertedType> {

    /**
     * Interface to do conversions to and from the two generic type parameters
     */
    interface TypeConverter<UnconvertedType, ConvertedType> {
        /**
         * Convert a native value
         * @param value The value to be converted
         * @return The conversion results
         */
        ConvertedType toConvertedType(UnconvertedType value);

        /**
         * Undo a conversion and restore the original native type
         * @param value The converted value
         * @return The original native type
         */
        UnconvertedType toUnconvertedType(ConvertedType value);
    }

    /**
     * Invokes {@link Headers#get(Object)} and does a conversion on the results if not {@code null}
     * @param name The name of entry to get
     * @return The value corresponding to {@code name} and then converted
     */
    ConvertedType getAndConvert(UnconvertedType name);

    /**
     * Invokes {@link Headers#get(Object, Object)} and does a conversion on the results if not {@code null}
     * @param name The name of entry to get
     * @return The value corresponding to {@code name} and then converted
     */
    ConvertedType getAndConvert(UnconvertedType name, ConvertedType defaultValue);

    /**
     * Invokes {@link Headers#getAndRemove(Object)} and does a conversion on the results if not {@code null}
     * @param name The name of entry to get
     * @return The value corresponding to {@code name} and then converted
     */
    ConvertedType getAndRemoveAndConvert(UnconvertedType name);

    /**
     * Invokes {@link Headers#getAndRemove(Object, Object)} and does
     * a conversion on the results if not {@code null}
     * @param name The name of entry to get
     * @return The value corresponding to {@code name} and then converted
     */
    ConvertedType getAndRemoveAndConvert(UnconvertedType name, ConvertedType defaultValue);

    /**
     * Invokes {@link Headers#getAll(Object)} and does a conversion on the results if not {@code null}
     * @param name The name of entry to get
     * @return The values corresponding to {@code name} and then converted
     */
    List<ConvertedType> getAllAndConvert(UnconvertedType name);

    /**
     * Invokes {@link Headers#getAllAndRemove(Object)} and does a conversion on the results if not {@code null}
     * @param name The name of entry to get
     * @return The values corresponding to {@code name} and then converted
     */
    List<ConvertedType> getAllAndRemoveAndConvert(UnconvertedType name);

    /**
     * Invokes {@link Headers#iterator()} and lazily does a conversion on the results as they are accessed
     *
     * @return Iterator which will provide converted values corresponding to {@code name}
     */
    Iterator<Entry<ConvertedType, ConvertedType>> iteratorConverted();

    /**
     * Invokes {@link Headers#names()} and does a conversion on the results
     *
     * @return The values corresponding to {@code name} and then converted
     */
    Set<ConvertedType> namesAndConvert(Comparator<ConvertedType> comparator);
}
