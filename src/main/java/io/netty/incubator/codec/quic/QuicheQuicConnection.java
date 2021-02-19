/*
 * Copyright 2021 The Netty Project
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
package io.netty.incubator.codec.quic;

import io.netty.util.ReferenceCounted;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetectorFactory;
import io.netty.util.ResourceLeakTracker;

final class QuicheQuicConnection {
    private static final ResourceLeakDetector<QuicheQuicConnection> LEAK_DETECTOR =
            ResourceLeakDetectorFactory.instance().newResourceLeakDetector(QuicheQuicConnection.class);
    private long connection;
    private final ReferenceCounted refCnt;
    private final ResourceLeakTracker<QuicheQuicConnection> tracker;

    QuicheQuicConnection(long connection, ReferenceCounted refCnt) {
        this.connection = connection;
        this.refCnt = refCnt;
        tracker = LEAK_DETECTOR.track(this);
    }

    // This should not need to be synchronized as it will either be called from the EventLoop thread or
    // the finalizer (in which case there can't be concurrent access here).
    void free() {
        if (connection != -1) {
            try {
                Quiche.quiche_conn_free(connection);
                refCnt.release();
                if (tracker != null) {
                    tracker.close(this);
                }
            } finally {
                connection = -1;
            }
        }
    }

    long address() {
        assert connection != -1;
        return connection;
    }

    boolean isClosed() {
        assert connection != -1;
        return Quiche.quiche_conn_is_closed(connection);
    }

    // Let's override finalize() as we want to ensure we never leak memory even if the user will miss to close
    // Channel that uses this connection and just let it get GC'ed
    @Override
    protected void finalize() throws Throwable {
        try {
            free();
        } finally {
            super.finalize();
        }
    }
}
