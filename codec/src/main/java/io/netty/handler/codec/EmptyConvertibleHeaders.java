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

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

public class EmptyConvertibleHeaders<UnconvertedType, ConvertedType> extends
        EmptyHeaders<UnconvertedType> implements ConvertibleHeaders<UnconvertedType, ConvertedType> {

    @Override
    public ConvertedType getAndConvert(UnconvertedType name) {
        return null;
    }

    @Override
    public ConvertedType getAndConvert(UnconvertedType name, ConvertedType defaultValue) {
        return defaultValue;
    }

    @Override
    public ConvertedType getAndRemoveAndConvert(UnconvertedType name) {
        return null;
    }

    @Override
    public ConvertedType getAndRemoveAndConvert(UnconvertedType name, ConvertedType defaultValue) {
        return defaultValue;
    }

    @Override
    public List<ConvertedType> getAllAndConvert(UnconvertedType name) {
        return Collections.emptyList();
    }

    @Override
    public List<ConvertedType> getAllAndRemoveAndConvert(UnconvertedType name) {
        return Collections.emptyList();
    }

    @Override
    public List<Entry<ConvertedType, ConvertedType>> entriesConverted() {
        return Collections.emptyList();
    }

    @Override
    public Iterator<Entry<ConvertedType, ConvertedType>> iteratorConverted() {
        return entriesConverted().iterator();
    }

    @Override
    public Set<ConvertedType> namesAndConvert(Comparator<ConvertedType> comparator) {
        return Collections.emptySet();
    }
}
