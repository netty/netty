/*
 * Copyright 2015 The Netty Project
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
package io.netty.channel.epoll;

import io.netty.util.internal.ObjectUtil;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;

final class TcpMd5Util {

    static Collection<InetAddress> newTcpMd5Sigs(AbstractEpollChannel channel, Collection<InetAddress> current,
                                         Map<InetAddress, byte[]> newKeys) throws IOException {
        ObjectUtil.checkNotNull(channel, "channel");
        ObjectUtil.checkNotNull(current, "current");
        ObjectUtil.checkNotNull(newKeys, "newKeys");

        // Validate incoming values
        for (Entry<InetAddress, byte[]> e : newKeys.entrySet()) {
            final byte[] key = e.getValue();
            if (e.getKey() == null) {
                throw new IllegalArgumentException("newKeys contains an entry with null address: " + newKeys);
            }
            ObjectUtil.checkNotNull(key, "newKeys[" + e.getKey() + ']');
            if (key.length == 0) {
                throw new IllegalArgumentException("newKeys[" + e.getKey() + "] has an empty key.");
            }
            if (key.length > Native.TCP_MD5SIG_MAXKEYLEN) {
                throw new IllegalArgumentException("newKeys[" + e.getKey() +
                    "] has a key with invalid length; should not exceed the maximum length (" +
                        Native.TCP_MD5SIG_MAXKEYLEN + ')');
            }
        }

        // Remove mappings not present in the new set.
        for (InetAddress addr : current) {
            if (!newKeys.containsKey(addr)) {
                channel.socket.setTcpMd5Sig(addr, null);
            }
        }

        if (newKeys.isEmpty()) {
            return Collections.emptySet();
        }

        // Set new mappings and store addresses which we set.
        final Collection<InetAddress> addresses = new ArrayList<InetAddress>(newKeys.size());
        for (Entry<InetAddress, byte[]> e : newKeys.entrySet()) {
            channel.socket.setTcpMd5Sig(e.getKey(), e.getValue());
            addresses.add(e.getKey());
        }

        return addresses;
    }

    private TcpMd5Util() {
    }
}
