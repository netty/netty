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

/**
 * Transport parameters for QUIC.
 */
public interface QuicTransportParameters {

    /**
     * The maximum idle timeout.
     * @return timeout.
     */
    long maxIdleTimeout();

    /**
     * The maximum UDP payload size.
     *
     * @return maximum payload size.
     */
    long maxUdpPayloadSize();

    /**
     * The initial flow control maximum data for the connection.
     *
     * @return flowcontrol.
     */
    long initialMaxData();

    /**
     * The initial flow control maximum data for local bidirectional streams.
     *
     * @return flowcontrol.
     */
    long initialMaxStreamDataBidiLocal();

    /**
     * The initial flow control maximum data for remote bidirectional streams.
     *
     * @return flowcontrol.
     */
    long initialMaxStreamDataBidiRemote();

    /**
     * The initial flow control maximum data for unidirectional streams.
     *
     * @return flowcontrol.
     */
    long initialMaxStreamDataUni();

    /**
     * The initial maximum bidirectional streams.
     *
     * @return streams.
     */
    long initialMaxStreamsBidi();

    /**
     * The initial maximum unidirectional streams.
     *
     * @return streams.
     */
    long initialMaxStreamsUni();

    /**
     * The ACK delay exponent
     *
     * @return exponent.
     */
    long ackDelayExponent();

    /**
     * The max ACK delay.
     *
     * @return delay.
     */
    long maxAckDelay();

    /**
     * Whether active migration is disabled.
     *
     * @return disabled.
     */
    boolean disableActiveMigration();

    /**
     * The active connection ID limit.
     *
     * @return limit.
     */
    long activeConnIdLimit();

    /**
     * DATAGRAM frame extension parameter, if any.
     *
     * @return param.
     */
    long maxDatagramFrameSize();
}
