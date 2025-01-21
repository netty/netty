/*
 * Copyright 2020 The Netty Project
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

/**
 * Represents options defined in a file of the format <a href=https://linux.die.net/man/5/resolver>etc/resolv.conf</a>.
 */
final class UnixResolverOptions {

    private final int ndots;
    private final int timeout;
    private final int attempts;

    UnixResolverOptions(int ndots, int timeout, int attempts) {
        this.ndots = ndots;
        this.timeout = timeout;
        this.attempts = attempts;
    }

    static UnixResolverOptions.Builder newBuilder() {
        return new UnixResolverOptions.Builder();
    }

    /**
     * The number of dots which must appear in a name before an initial absolute query is made.
     * The default value is {@code 1}.
     */
    int ndots() {
        return ndots;
    }

    /**
     * The timeout of each DNS query performed by this resolver (in seconds).
     * The default value is {@code 5}.
     */
    int timeout() {
        return timeout;
    }

    /**
     * The maximum allowed number of DNS queries to send when resolving a host name.
     * The default value is {@code 16}.
     */
    int attempts() {
        return attempts;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
                "{ndots=" + ndots +
                ", timeout=" + timeout +
                ", attempts=" + attempts +
                '}';
    }

    static final class Builder {

        private int ndots = 1;
        private int timeout = 5;
        private int attempts = 16;

        private Builder() {
        }

        void setNdots(int ndots) {
            this.ndots = ndots;
        }

        void setTimeout(int timeout) {
            this.timeout = timeout;
        }

        void setAttempts(int attempts) {
            this.attempts = attempts;
        }

        UnixResolverOptions build() {
            return new UnixResolverOptions(ndots, timeout, attempts);
        }
    }
}
