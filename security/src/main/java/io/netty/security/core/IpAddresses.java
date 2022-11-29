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
package io.netty.security.core;

import io.netty.util.internal.ObjectUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * {@link IpAddresses} contains multiple {@link IpAddress} and provide lookup of
 * using {@link IpAddressLookup}.
 * Note: this class has a natural ordering that is inconsistent with equals.
 */
public class IpAddresses extends SafeListController<Address> implements IpAddressLookup, Comparable<IpAddresses> {

    public static final class AcceptAnyIpAddresses extends IpAddresses {
        public static final AcceptAnyIpAddresses INSTANCE = new AcceptAnyIpAddresses();

        private AcceptAnyIpAddresses() {
            super(Collections.singletonList(Address.ANY_ADDRESS));
        }

        @Override
        public boolean equals(Object o) {
            return getClass() == o.getClass();
        }

        @Override
        public int lookup(Address address) {
            return 0;
        }

        @Override
        public boolean lookupAddress(Address address) {
            return true;
        }

        @Override
        public int compareTo(IpAddresses ipAddresses) {
            return 0;
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }
    }

    private IpAddresses(List<Address> addresses) {
        super(new SortAndFilterImpl());
        ObjectUtil.checkNotNull(addresses, "List");
        MAIN_LIST.addAll(addresses);
        unlockAndLock();
    }

    /**
     * Create a new {@link IpAddresses} instance with specified {@link List} of {@link Address}
     *
     * @param ipAddresses {@link List} of {@link Address}
     * @return {@link IpAddresses} new instance
     */
    public static IpAddresses create(List<Address> ipAddresses) {
        return new IpAddresses(ipAddresses);
    }

    /**
     * Create a new {@link IpAddresses} instance with specified {@link List} of {@link Address}
     *
     * @param ipAddresses {@link IpAddress}s
     * @return {@link IpAddresses} new instance
     */
    public static IpAddresses create(Address... ipAddresses) {
        return new IpAddresses(Arrays.asList(ipAddresses));
    }

    /**
     * Create a new {@link IpAddresses} instance without any {@link Address}
     *
     * @return {@link IpAddresses} new instance
     */
    public static IpAddresses create() {
        return new IpAddresses(Collections.<Address>emptyList());
    }

    @Override
    public int lookup(Address address) {
        // Perform lookup using Binary Search.
        // If return index is zero or higher, return true else false.
        return Collections.binarySearch(MAIN_LIST, address);
    }

    @Override
    public boolean lookupAddress(Address address) {
        // If return is whole number then lookup was successful.
        // If return is negative number then lookup was unsuccessful.
        return lookup(address) >= 0;
    }

    @Override
    public String toString() {
        return "IpAddresses{List=" + MAIN_LIST + '}';
    }

    @Override
    public int compareTo(IpAddresses ipAddresses) {
        // If current list size is bigger than comparator list size then return 1;
        // If current list size is smaller than comparator list size then return -1;
        if (MAIN_LIST.size() > ipAddresses.MAIN_LIST.size()) {
            return 1;
        }
        if (MAIN_LIST.size() < ipAddresses.MAIN_LIST.size()) {
            return -1;
        }

        // Iterate over current list and comparator list at the same time
        // and compare Ip Address.
        for (int i = 0; i < MAIN_LIST.size(); i++) {
            int compare = ipAddresses.MAIN_LIST.get(i).compareTo(MAIN_LIST.get(i));
            if (compare != 0) {
                return compare;
            }
        }

        return 0;
    }

    private static final class SortAndFilterImpl implements SortAndFilter<Address> {

        /**
         * <ol>
         *     <li> Sort the list </li>
         *     <li> Remove over-lapping subnet </li>
         *     <li> Sort the list again </li>
         * </ol>
         */
        @SuppressWarnings("ConstantConditions")
        @Override
        public List<Address> process(List<Address> list) {
            Collections.sort(list);
            Iterator<Address> iterator = list.iterator();
            List<Address> toKeep = new ArrayList<Address>();

            Address parentRule = iterator.hasNext() ? iterator.next() : null;
            if (parentRule != null) {
                toKeep.add(parentRule);
            }

            while (iterator.hasNext()) {

                // Grab a potential child rule.
                Address childRule = iterator.next();

                // If parentRule matches childRule, then there's no need to keep the child rule.
                // Otherwise, the rules are distinct, and we need both.
                //
                // parentRule can never be null.
                if (parentRule.compareTo(childRule) == 0) {
                    toKeep.add(childRule);
                    // Then we'll keep the child rule around as the parent for the next round.
                    parentRule = childRule;
                }
            }

            Collections.sort(toKeep);
            return toKeep;
        }
    }
}
