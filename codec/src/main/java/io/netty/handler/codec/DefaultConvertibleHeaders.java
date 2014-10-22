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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

public class DefaultConvertibleHeaders<UnconvertedType, ConvertedType> extends DefaultHeaders<UnconvertedType>
        implements ConvertibleHeaders<UnconvertedType, ConvertedType> {

    private final TypeConverter<UnconvertedType, ConvertedType> typeConverter;

    public DefaultConvertibleHeaders(Comparator<? super UnconvertedType> keyComparator,
            Comparator<? super UnconvertedType> valueComparator,
            HashCodeGenerator<UnconvertedType> hashCodeGenerator,
            ValueConverter<UnconvertedType> valueConverter,
            TypeConverter<UnconvertedType, ConvertedType> typeConverter) {
        super(keyComparator, valueComparator, hashCodeGenerator, valueConverter);
        this.typeConverter = typeConverter;
    }

    public DefaultConvertibleHeaders(Comparator<? super UnconvertedType> keyComparator,
            Comparator<? super UnconvertedType> valueComparator,
            HashCodeGenerator<UnconvertedType> hashCodeGenerator,
            ValueConverter<UnconvertedType> valueConverter,
            TypeConverter<UnconvertedType, ConvertedType> typeConverter,
            NameConverter<UnconvertedType> nameConverter) {
        super(keyComparator, valueComparator, hashCodeGenerator, valueConverter, nameConverter);
        this.typeConverter = typeConverter;
    }

    @Override
    public ConvertedType getAndConvert(UnconvertedType name) {
        return getAndConvert(name, null);
    }

    @Override
    public ConvertedType getAndConvert(UnconvertedType name, ConvertedType defaultValue) {
        UnconvertedType v = get(name);
        if (v == null) {
            return defaultValue;
        }
        return typeConverter.toConvertedType(v);
    }

    @Override
    public ConvertedType getAndRemoveAndConvert(UnconvertedType name) {
        return getAndRemoveAndConvert(name, null);
    }

    @Override
    public ConvertedType getAndRemoveAndConvert(UnconvertedType name, ConvertedType defaultValue) {
        UnconvertedType v = getAndRemove(name);
        if (v == null) {
            return defaultValue;
        }
        return typeConverter.toConvertedType(v);
    }

    @Override
    public List<ConvertedType> getAllAndConvert(UnconvertedType name) {
        List<UnconvertedType> all = getAll(name);
        List<ConvertedType> allConverted = new ArrayList<ConvertedType>(all.size());
        for (int i = 0; i < all.size(); ++i) {
            allConverted.add(typeConverter.toConvertedType(all.get(i)));
        }
        return allConverted;
    }

    @Override
    public List<ConvertedType> getAllAndRemoveAndConvert(UnconvertedType name) {
        List<UnconvertedType> all = getAllAndRemove(name);
        List<ConvertedType> allConverted = new ArrayList<ConvertedType>(all.size());
        for (int i = 0; i < all.size(); ++i) {
            allConverted.add(typeConverter.toConvertedType(all.get(i)));
        }
        return allConverted;
    }

    @Override
    public List<Entry<ConvertedType, ConvertedType>> entriesConverted() {
        List<Entry<UnconvertedType, UnconvertedType>> entries = entries();
        List<Entry<ConvertedType, ConvertedType>> entriesConverted = new ArrayList<Entry<ConvertedType, ConvertedType>>(
                entries.size());
        for (int i = 0; i < entries.size(); ++i) {
            entriesConverted.add(new ConvertedEntry(entries.get(i)));
        }
        return entriesConverted;
    }

    @Override
    public Iterator<Entry<ConvertedType, ConvertedType>> iteratorConverted() {
        return new ConvertedIterator();
    }

    @Override
    public Set<ConvertedType> namesAndConvert(Comparator<ConvertedType> comparator) {
        Set<UnconvertedType> names = names();
        Set<ConvertedType> namesConverted = new TreeSet<ConvertedType>(comparator);
        for (UnconvertedType unconverted : names) {
            namesConverted.add(typeConverter.toConvertedType(unconverted));
        }
        return namesConverted;
    }

    private final class ConvertedIterator implements Iterator<Entry<ConvertedType, ConvertedType>> {
        private final Iterator<Entry<UnconvertedType, UnconvertedType>> iter = iterator();

        @Override
        public boolean hasNext() {
            return iter.hasNext();
        }

        @Override
        public Entry<ConvertedType, ConvertedType> next() {
            Entry<UnconvertedType, UnconvertedType> next = iter.next();

            return new ConvertedEntry(next);
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    private final class ConvertedEntry implements Entry<ConvertedType, ConvertedType> {
        private final Entry<UnconvertedType, UnconvertedType> entry;
        private ConvertedType name;
        private ConvertedType value;

        ConvertedEntry(Entry<UnconvertedType, UnconvertedType> entry) {
            this.entry = entry;
        }

        @Override
        public ConvertedType getKey() {
            if (name == null) {
                name = typeConverter.toConvertedType(entry.getKey());
            }
            return name;
        }

        @Override
        public ConvertedType getValue() {
            if (value == null) {
                value = typeConverter.toConvertedType(entry.getValue());
            }
            return value;
        }

        @Override
        public ConvertedType setValue(ConvertedType value) {
            ConvertedType old = getValue();
            entry.setValue(typeConverter.toUnconvertedType(value));
            return old;
        }

        @Override
        public String toString() {
            return entry.toString();
        }
    }
}
