/*
 * Copyright 2022 The Netty Project
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
package io.netty.security.core.standards;

import io.netty.security.core.FiveTuple;
import io.netty.security.core.Rule;
import io.netty.security.core.RuleLookup;
import io.netty.security.core.SafeListController;
import io.netty.security.core.Table;
import io.netty.util.internal.ObjectUtil;

import java.io.Serializable;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static io.netty.security.core.Util.compareIntegers;
import static io.netty.security.core.Util.hash;

/**
 * This is a standard implementation of {@link Table}.
 * It handles {@link Rule} lookup by implementing {@link RuleLookup}.
 * Modification of {@link Rule}s such as add or remove is supported.
 * Take a look at {@link SafeListController} to know how to add or remove rules.
 */
public final class StandardTable extends SafeListController<Rule> implements Table {
    private final int priority;
    private final String name;

    private StandardTable(int priority, String name) {
        super(SortAndFilterImpl.INSTANCE);
        this.priority = ObjectUtil.checkInRange(priority, 1, Integer.MAX_VALUE - 1, "Priority");
        this.name = ObjectUtil.checkNotNull(name, "Name");
    }

    /**
     * Create a new {@link StandardTable} instance
     *
     * @param priority Table priority
     * @param name     Table name
     * @return New {@link StandardTable} instance
     */
    public static StandardTable of(int priority, String name) {
        return new StandardTable(priority, name);
    }

    @Override
    public int priority() {
        return priority;
    }

    @Override
    public String name() {
        return name;
    }

    /**
     * Return an unmodifiable {@link List} of {@link Rule}.
     */
    @Override
    public List<Rule> rules() {
        return copy();
    }

    @Override
    public void addRule(Rule rule) {
        add(rule);
    }

    @Override
    public void removeRule(Rule rule) {
        remove(rule);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        StandardTable that = (StandardTable) o;
        return hashCode() == that.hashCode();
    }

    @Override
    public int compareTo(Table table) {
        return compareIntegers(priority(), table.priority());
    }

    @Override
    public int hashCode() {
        return hash(priority);
    }

    @Override
    public Rule lookup(FiveTuple fiveTuple) {
        int index = Collections.binarySearch(MAIN_LIST, fiveTuple, BinarySearchFiveTupleComparator.INSTANCE);

        if (index >= 0) {
            return MAIN_LIST.get(index);
        } else {
            // If rule was not found, return null.
            return null;
        }
    }

    private static final class SortAndFilterImpl implements SortAndFilter<Rule> {

        private static final SortAndFilterImpl INSTANCE = new SortAndFilterImpl();

        @Override
        public List<Rule> process(List<Rule> list) {
            Collections.sort(list);
            return list;
        }
    }

    @SuppressWarnings("ComparatorNotSerializable")
    private static final class BinarySearchFiveTupleComparator implements Comparator<Object> {
        private static final BinarySearchFiveTupleComparator INSTANCE = new BinarySearchFiveTupleComparator();

        @Override
        public int compare(Object o1, Object o2) {
            Rule rule = (Rule) o1;
            FiveTuple fiveTuple = (FiveTuple) o2;

            int compare = compareIntegers(rule.protocol().ordinal(), fiveTuple.protocol().ordinal());
            if (compare != 0) {
                return compare;
            }

            compare = rule.sourcePorts().lookup(fiveTuple.sourcePort());
            if (compare != 0) {
                return compare;
            }

            compare = rule.destinationPorts().lookup(fiveTuple.destinationPort());
            if (compare != 0) {
                return compare;
            }

            compare = rule.sourceIpAddresses().lookup(fiveTuple.sourceIpAddress());
            if (compare != 0) {
                return compare;
            }

            return rule.destinationIpAddresses().lookup(fiveTuple.destinationIpAddress());
        }
    }
}
