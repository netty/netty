/*
 * Copyright 2023 The Netty Project
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
package io.netty.handler.codec.quic;

import java.net.InetSocketAddress;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * A network path specific {@link QuicEvent}.
 */
public abstract class QuicPathEvent implements QuicEvent {

    private final InetSocketAddress local;
    private final InetSocketAddress remote;

    QuicPathEvent(InetSocketAddress local, InetSocketAddress remote) {
        this.local = requireNonNull(local, "local");
        this.remote = requireNonNull(remote, "remote");
    }

    /**
     * The local address of the network path.
     *
     * @return  local
     */
    public InetSocketAddress local() {
        return local;
    }

    /**
     * The remote address of the network path.
     *
     * @return  local
     */
    public InetSocketAddress remote() {
        return remote;
    }

    @Override
    public String toString() {
        return "QuicPathEvent{" +
                "local=" + local +
                ", remote=" + remote +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        QuicPathEvent that = (QuicPathEvent) o;
        if (!Objects.equals(local, that.local)) {
            return false;
        }
        return Objects.equals(remote, that.remote);
    }

    @Override
    public int hashCode() {
        int result = local != null ? local.hashCode() : 0;
        result = 31 * result + (remote != null ? remote.hashCode() : 0);
        return result;
    }

    public static final class New extends QuicPathEvent {
        /**
         * A new network path (local address, remote address) has been seen on a received packet.
         * Note that this event is only triggered for servers, as the client is responsible from initiating new paths.
         * The application may then probe this new path, if desired.
         *
         * @param local     local address.
         * @param remote    remote address.
         */
        public New(InetSocketAddress local, InetSocketAddress remote) {
            super(local, remote);
        }

        @Override
        public String toString() {
            return "QuicPathEvent.New{" +
                    "local=" + local() +
                    ", remote=" + remote() +
                    '}';
        }
    }

    public static final class Validated extends QuicPathEvent {
        /**
         * The related network path between local and remote has been validated.
         *
         * @param local     local address.
         * @param remote    remote address.
         */
        public Validated(InetSocketAddress local, InetSocketAddress remote) {
            super(local, remote);
        }

        @Override
        public String toString() {
            return "QuicPathEvent.Validated{" +
                    "local=" + local() +
                    ", remote=" + remote() +
                    '}';
        }
    }

    public static final class FailedValidation extends QuicPathEvent {
        /**
         * The related network path between local and remote failed to be validated.
         * This network path will not be used anymore, unless the application requests probing this path again.
         *
         * @param local     local address.
         * @param remote    remote address.
         */
        public FailedValidation(InetSocketAddress local, InetSocketAddress remote) {
            super(local, remote);
        }

        @Override
        public String toString() {
            return "QuicPathEvent.FailedValidation{" +
                    "local=" + local() +
                    ", remote=" + remote() +
                    '}';
        }
    }

    public static final class Closed extends QuicPathEvent {

        /**
         * The related network path between local and remote has been closed and is now unusable on this connection.
         *
         * @param local     local address.
         * @param remote    remote address.
         */
        public Closed(InetSocketAddress local, InetSocketAddress remote) {
            super(local, remote);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            return super.equals(o);
        }

        @Override
        public int hashCode() {
            return 31 * super.hashCode();
        }

        @Override
        public String toString() {
            return "QuicPathEvent.Closed{" +
                    "local=" + local() +
                    ", remote=" + remote() +
                    '}';
        }
    }

    public static final class ReusedSourceConnectionId extends QuicPathEvent {
        private final long seq;
        private final InetSocketAddress oldLocal;
        private final InetSocketAddress oldRemote;

        /**
         * The stack observes that the Source Connection ID with the given sequence number,
         * initially used by the peer over the first pair of addresses, is now reused over
         * the second pair of addresses.
         *
         * @param seq           sequence number
         * @param oldLocal      old local address.
         * @param oldRemote     old remote address.
         * @param local         local address.
         * @param remote        remote address.
         */
        public ReusedSourceConnectionId(long seq, InetSocketAddress oldLocal, InetSocketAddress oldRemote,
                                       InetSocketAddress local, InetSocketAddress remote) {
            super(local, remote);
            this.seq = seq;
            this.oldLocal = requireNonNull(oldLocal, "oldLocal");
            this.oldRemote = requireNonNull(oldRemote, "oldRemote");
        }

        /**
         * Source connection id sequence number.
         *
         * @return  sequence number
         */
        public long seq() {
            return seq;
        }

        /**
         * The old local address of the network path.
         *
         * @return  local
         */
        public InetSocketAddress oldLocal() {
            return oldLocal;
        }

        /**
         * The old remote address of the network path.
         *
         * @return  local
         */
        public InetSocketAddress oldRemote() {
            return oldRemote;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            if (!super.equals(o)) {
                return false;
            }

            ReusedSourceConnectionId that = (ReusedSourceConnectionId) o;

            if (seq != that.seq) {
                return false;
            }
            if (!Objects.equals(oldLocal, that.oldLocal)) {
                return false;
            }
            return Objects.equals(oldRemote, that.oldRemote);
        }

        @Override
        public int hashCode() {
            int result = super.hashCode();
            result = 31 * result + (int) (seq ^ (seq >>> 32));
            result = 31 * result + (oldLocal != null ? oldLocal.hashCode() : 0);
            result = 31 * result + (oldRemote != null ? oldRemote.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "QuicPathEvent.ReusedSourceConnectionId{" +
                    "seq=" + seq +
                    ", oldLocal=" + oldLocal +
                    ", oldRemote=" + oldRemote +
                    ", local=" + local() +
                    ", remote=" + remote() +
                    '}';
        }
    }

    public static final class PeerMigrated extends QuicPathEvent {

        /**
         * The connection observed that the remote migrated over the network path denoted by the pair of addresses,
         * i.e., non-probing packets have been received on this network path. This is a server side only event.
         * Note that this event is only raised if the path has been validated.
         *
         * @param local     local address.
         * @param remote    remote address.
         */
        public PeerMigrated(InetSocketAddress local, InetSocketAddress remote) {
            super(local, remote);
        }

        @Override
        public String toString() {
            return "QuicPathEvent.PeerMigrated{" +
                    "local=" + local() +
                    ", remote=" + remote() +
                    '}';
        }
    }
}
