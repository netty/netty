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

package io.netty.resolver.dns;

import io.netty.channel.AddressedEnvelope;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ScheduledFuture;
import io.netty.util.internal.OneTimeTask;
import io.netty.util.internal.PlatformDependent;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

final class DnsCacheEntry {

    private enum State {
        INIT,
        SCHEDULED_EXPIRATION,
        RELEASED
    }

    private final AddressedEnvelope<DnsResponse, InetSocketAddress> response;
    private final Throwable cause;
    private volatile ScheduledFuture<?> expirationFuture;
    private boolean released;

    @SuppressWarnings("unchecked")
    DnsCacheEntry(AddressedEnvelope<? extends DnsResponse, InetSocketAddress> response) {
        this.response = (AddressedEnvelope<DnsResponse, InetSocketAddress>) response.retain();
        cause = null;
    }

    DnsCacheEntry(Throwable cause) {
        this.cause = cause;
        response = null;
    }

    Throwable cause() {
        return cause;
    }

    synchronized AddressedEnvelope<DnsResponse, InetSocketAddress> retainedResponse() {
        if (released) {
            // Released by other thread via either the expiration task or clearCache()
            return null;
        }

        return response.retain();
    }

    void scheduleExpiration(EventLoop loop, Runnable task, long delay, TimeUnit unit) {
        assert expirationFuture == null: "expiration task scheduled already";
        expirationFuture = loop.schedule(task, delay, unit);
    }

    void release() {
        synchronized (this) {
            if (released) {
                return;
            }

            released = true;
            ReferenceCountUtil.safeRelease(response);
        }

        ScheduledFuture<?> expirationFuture = this.expirationFuture;
        if (expirationFuture != null) {
            expirationFuture.cancel(false);
        }
    }
}
