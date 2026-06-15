/*
 * Copyright 2026 The Netty Project
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

import io.netty.channel.AddressedEnvelope;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Promise;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for the missing {@code return;} in
 * {@link DnsQueryContext#writeQuery(boolean)} after
 * {@code finishFailure()} is called when the query-ID space is exhausted.
 *
 * <p>When {@code queryContextManager.add(...)} returns {@code -1} the method
 * must fail the promise and return immediately.  Without the fix, execution
 * falls through: a second {@code queryLifecycleObserver.queryWritten()} call
 * is made, {@code newQuery(-1, ...)} builds a bogus datagram with id 65535,
 * and {@code sendQuery()} actually writes it to the channel.
 */
public class DnsQueryContextTest {

    /**
     * Regression test: when the DNS transaction-ID space is exhausted,
     * {@code writeQuery} must NOT write any message to the channel.
     *
     * <p>The bug: a missing {@code return;} after {@code finishFailure()} caused
     * fall-through into {@code sendQuery()}, writing a query with id -1 (encoded
     * as 65535) to the channel even though the promise was already failed.
     *
     * <p>This test is intentionally RED against the unfixed code: with the bug
     * present, {@code channel.readOutbound()} returns a non-null
     * {@link io.netty.handler.codec.dns.DatagramDnsQuery}, causing the assertion
     * {@code assertNull(outbound)} to fail.
     */
    @Test
    public void writeQueryMustNotSendWhenIdSpaceExhausted() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            InetSocketAddress nameServerAddr =
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), 53);

            DnsQueryContextManager manager = new DnsQueryContextManager();

            // Exhaust the entire ID space for nameServerAddr.
            // DnsQueryIdSpace.maxUsableIds() == 65536 (IDs 0..65535).
            // We need one dummy context object to fill the manager; reusing the
            // same instance is safe because DnsQueryContextMap stores contexts
            // by ID (which is unique per add()), not by identity.
            //
            // We create the dummy with a separate promise so that it is never
            // written and does not interfere with the context-under-test.
            Promise<AddressedEnvelope<DnsResponse, InetSocketAddress>> dummyPromise =
                    channel.eventLoop().newPromise();
            DatagramDnsQueryContext dummyCtx = new DatagramDnsQueryContext(
                    channel,
                    nameServerAddr,
                    manager,
                    NoopDnsQueryLifecycleObserver.INSTANCE,
                    /* maxPayLoadSize */ 4096,
                    /* recursionDesired */ true,
                    /* queryTimeoutMillis */ 5000,
                    new DefaultDnsQuestion("dummy.netty.io.", DnsRecordType.A),
                    new DnsRecord[0],
                    dummyPromise,
                    /* socketBootstrap */ null,
                    /* retryWithTcpOnTimeout */ false);

            // Drain all 65536 IDs from the id space for nameServerAddr.
            DnsQueryIdSpace idSpace = new DnsQueryIdSpace();
            int maxIds = idSpace.maxUsableIds(); // 65536

            for (int i = 0; i < maxIds; i++) {
                int assignedId = manager.add(nameServerAddr, dummyCtx);
                assertThat(assignedId)
                        .as("Expected a valid id during exhaustion, got -1 at iteration %d", i)
                        .isGreaterThanOrEqualTo(0);
            }

            // The next add must return -1 — the space is now full.
            int overflowId = manager.add(nameServerAddr, dummyCtx);
            assertEquals(-1, overflowId,
                    "Expected -1 when ID space is exhausted");

            // Build the context-under-test that will be subjected to writeQuery.
            Promise<AddressedEnvelope<DnsResponse, InetSocketAddress>> testPromise =
                    channel.eventLoop().newPromise();
            DatagramDnsQueryContext ctx = new DatagramDnsQueryContext(
                    channel,
                    nameServerAddr,
                    manager,
                    NoopDnsQueryLifecycleObserver.INSTANCE,
                    /* maxPayLoadSize */ 4096,
                    /* recursionDesired */ true,
                    /* queryTimeoutMillis */ 5000,
                    new DefaultDnsQuestion("test.netty.io.", DnsRecordType.A),
                    new DnsRecord[0],
                    testPromise,
                    /* socketBootstrap */ null,
                    /* retryWithTcpOnTimeout */ false);

            // Invoke writeQuery — this should detect id == -1, call finishFailure(),
            // and return WITHOUT writing anything to the channel.
            ctx.writeQuery(true);

            // ---- Assertions ----

            // 1. The promise must be completed and failed (correct behaviour both
            //    before and after the fix).
            assertTrue(testPromise.isDone(),
                    "Promise should be done after writeQuery with exhausted ID space");
            Throwable cause = testPromise.cause();
            assertInstanceOf(DnsNameResolverException.class, cause,
                    "Promise should be failed with a DnsNameResolverException when ID space is exhausted");
            assertInstanceOf(IllegalStateException.class, cause.getCause(),
                    "The root cause should be the IllegalStateException reporting the exhausted ID space");

            // 2. No outbound message should have been written to the channel.
            //    With the bug present the fall-through reaches sendQuery(), which
            //    calls channel.writeAndFlush(query) with a bogus id (-1/65535),
            //    so readOutbound() returns a non-null DatagramDnsQuery — the test
            //    fails here on unfixed code, confirming the RED state.
            Object outbound = channel.readOutbound();
            // Release the outbound message regardless of outcome so that the
            // reference-counted DatagramDnsQuery (written by the buggy fall-through)
            // does not trigger a secondary memory-leak error that would obscure the
            // primary assertion failure.
            ReferenceCountUtil.release(outbound);
            assertNull(outbound,
                    "No DNS query must be written to the channel when ID space is exhausted, " +
                    "but found: " + outbound);
        } finally {
            channel.finishAndReleaseAll();
        }
    }
}
