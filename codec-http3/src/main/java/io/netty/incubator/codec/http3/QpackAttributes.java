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

package io.netty.incubator.codec.http3;

import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;

import static java.util.Objects.requireNonNull;

final class QpackAttributes {
    private final QuicChannel channel;
    private final boolean dynamicTableDisabled;
    private final Promise<QuicStreamChannel> encoderStreamPromise;
    private final Promise<QuicStreamChannel> decoderStreamPromise;

    private QuicStreamChannel encoderStream;
    private QuicStreamChannel decoderStream;

    QpackAttributes(QuicChannel channel, boolean disableDynamicTable) {
        this.channel = channel;
        dynamicTableDisabled = disableDynamicTable;
        encoderStreamPromise = dynamicTableDisabled ? null : channel.eventLoop().newPromise();
        decoderStreamPromise = dynamicTableDisabled ? null : channel.eventLoop().newPromise();
    }

    boolean dynamicTableDisabled() {
        return dynamicTableDisabled;
    }

    boolean decoderStreamAvailable() {
        return !dynamicTableDisabled && decoderStream != null;
    }

    boolean encoderStreamAvailable() {
        return !dynamicTableDisabled && encoderStream != null;
    }

    void whenEncoderStreamAvailable(GenericFutureListener<Future<? super QuicStreamChannel>> listener) {
        assert !dynamicTableDisabled;
        assert encoderStreamPromise != null;
        encoderStreamPromise.addListener(listener);
    }

    void whenDecoderStreamAvailable(GenericFutureListener<Future<? super QuicStreamChannel>> listener) {
        assert !dynamicTableDisabled;
        assert decoderStreamPromise != null;
        decoderStreamPromise.addListener(listener);
    }

    QuicStreamChannel decoderStream() {
        assert decoderStreamAvailable();
        return decoderStream;
    }

    QuicStreamChannel encoderStream() {
        assert encoderStreamAvailable();
        return encoderStream;
    }

    void decoderStream(QuicStreamChannel decoderStream) {
        assert channel.eventLoop().inEventLoop();
        assert !dynamicTableDisabled;
        assert decoderStreamPromise != null;
        assert this.decoderStream == null;
        this.decoderStream = requireNonNull(decoderStream);
        decoderStreamPromise.setSuccess(decoderStream);
    }

    void encoderStream(QuicStreamChannel encoderStream) {
        assert channel.eventLoop().inEventLoop();
        assert !dynamicTableDisabled;
        assert encoderStreamPromise != null;
        assert this.encoderStream == null;
        this.encoderStream = requireNonNull(encoderStream);
        encoderStreamPromise.setSuccess(encoderStream);
    }

    void encoderStreamInactive(Throwable cause) {
        assert channel.eventLoop().inEventLoop();
        assert !dynamicTableDisabled;
        assert encoderStreamPromise != null;
        encoderStreamPromise.tryFailure(cause);
    }

    void decoderStreamInactive(Throwable cause) {
        assert channel.eventLoop().inEventLoop();
        assert !dynamicTableDisabled;
        assert decoderStreamPromise != null;
        decoderStreamPromise.tryFailure(cause);
    }
}
