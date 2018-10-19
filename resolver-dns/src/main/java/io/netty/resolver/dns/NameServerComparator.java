/*
 * Copyright 2018 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.resolver.dns;

import io.netty.util.internal.ObjectUtil;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Comparator;
import java.util.List;

/**
 * Special {@link Comparator} implementation to sort the nameservers to use when follow redirects.
 *
 * This implementation follows all the semantics listed in the
 * <a href="https://docs.oracle.com/javase/8/docs/api/java/util/Comparator.html">Comparator apidocs</a>
 * with the limitation that {@link InetSocketAddress#equals(Object)} will not result in the same return value as
 * {@link #compare(InetSocketAddress, InetSocketAddress)}. This is completely fine as this should only be used
 * to sort {@link List}s.
 */
public final class NameServerComparator implements Comparator<InetSocketAddress>, Serializable {

    private static final long serialVersionUID = 8372151874317596185L;

    private final Class<? extends InetAddress> preferredAddressType;

    public NameServerComparator(Class<? extends InetAddress> preferredAddressType) {
        this.preferredAddressType = ObjectUtil.checkNotNull(preferredAddressType, "preferredAddressType");
    }

    @Override
    public int compare(InetSocketAddress addr1, InetSocketAddress addr2) {
        if (addr1.equals(addr2)) {
            return 0;
        }
        if (!addr1.isUnresolved() && !addr2.isUnresolved()) {
            if (addr1.getAddress().getClass() == addr2.getAddress().getClass()) {
                return 0;
            }
            return preferredAddressType.isAssignableFrom(addr1.getAddress().getClass()) ? -1 : 1;
        }
        if (addr1.isUnresolved() && addr2.isUnresolved()) {
            return 0;
        }
        return addr1.isUnresolved() ? 1 : -1;
    }
}
