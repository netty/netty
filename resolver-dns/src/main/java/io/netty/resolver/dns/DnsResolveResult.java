/*
 * Copyright 2025 The Netty Project
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
package io.netty.resolver.dns;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The result of a DNS resolution that includes both the resolved address and the CNAME chain
 * that was followed to reach that address.
 *
 * <p>The CNAME chain contains only the intermediate CNAME records that were followed during
 * resolution, not the original hostname. For example, if {@code www.example.com} resolves
 * via CNAMEs {@code cdn.example.com} → {@code server.fastly.com} to address {@code 192.0.2.1},
 * the CNAME chain will contain {@code ["cdn.example.com", "server.fastly.com"]}.
 *
 * @since 4.2.0
 */
public final class DnsResolveResult {

    private final InetAddress address;
    private final List<String> cnameChain;

    /**
     * Creates a new DNS resolve result.
     *
     * @param address the resolved IP address
     * @param cnameChain the CNAME chain that was followed to reach this address, may be empty
     */
    public DnsResolveResult(InetAddress address, List<String> cnameChain) {
        this.address = checkNotNull(address, "address");
        this.cnameChain = cnameChain == null || cnameChain.isEmpty()
            ? Collections.emptyList()
            : Collections.unmodifiableList(new ArrayList<String>(cnameChain));
    }

    /**
     * Returns the resolved IP address.
     *
     * @return the resolved IP address, never {@code null}
     */
    public InetAddress address() {
        return address;
    }

    /**
     * Returns the CNAME chain that was followed to reach this address.
     *
     * <p>The chain contains only intermediate CNAME records, not the original hostname.
     * If the address was resolved directly without following any CNAMEs, this will return
     * an empty list.
     *
     * @return the CNAME chain, never {@code null} but may be empty
     */
    public List<String> cnameChain() {
        return cnameChain;
    }

    /**
     * Returns {@code true} if this result was obtained by following one or more CNAME records.
     *
     * @return {@code true} if CNAME indirection was involved in this resolution
     */
    public boolean hasCnameIndirection() {
        return !cnameChain.isEmpty();
    }

    /**
     * Returns the final CNAME in the chain, or {@code null} if no CNAMEs were followed.
     *
     * @return the final CNAME in the resolution chain, or {@code null}
     */
    public String finalCname() {
        return cnameChain.isEmpty() ? null : cnameChain.get(cnameChain.size() - 1);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DnsResolveResult)) {
            return false;
        }
        DnsResolveResult that = (DnsResolveResult) o;
        return Objects.equals(address, that.address) &&
               Objects.equals(cnameChain, that.cnameChain);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address, cnameChain);
    }

    @Override
    public String toString() {
        if (cnameChain.isEmpty()) {
            return address.toString();
        }
        return address + " (via CNAMEs: " + cnameChain + ")";
    }
}

