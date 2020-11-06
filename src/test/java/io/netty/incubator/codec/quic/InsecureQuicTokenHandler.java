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
package io.netty.incubator.codec.quic;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.util.CharsetUtil;
import io.netty.util.NetUtil;

import java.net.InetSocketAddress;
import java.util.Arrays;

public final class InsecureQuicTokenHandler implements QuicTokenHandler {

    private static final String SERVER_NAME = "netty";
    private static final byte[] SERVER_NAME_BYTES = SERVER_NAME.getBytes(CharsetUtil.US_ASCII);

    private static final int MAX_TOKEN_LEN = Quiche.QUICHE_MAX_CONN_ID_LEN +
            NetUtil.LOCALHOST6.getAddress().length + SERVER_NAME_BYTES.length;

    private InsecureQuicTokenHandler() {
        Quic.ensureAvailability();
    }

    public static final InsecureQuicTokenHandler INSTANCE = new InsecureQuicTokenHandler();

    @Override
    public void writeToken(ByteBuf out, ByteBuf dcid, InetSocketAddress address) {
        byte[] addr = address.getAddress().getAddress();
        out.writeBytes(SERVER_NAME_BYTES)
                .writeBytes(addr)
                .writeBytes(dcid, dcid.readerIndex(), dcid.readableBytes());
    }

    @Override
    public int validateToken(ByteBuf token, InetSocketAddress address) {
        int minLength = SERVER_NAME_BYTES.length + address.getAddress().getAddress().length;
        if (token.readableBytes() <= SERVER_NAME_BYTES.length + address.getAddress().getAddress().length) {
            return -1;
        }

        // TODO: Optimize me
        byte[] bytes = ByteBufUtil.getBytes(token, 0, minLength);
        if (!Arrays.equals(SERVER_NAME_BYTES, Arrays.copyOfRange(bytes, 0, SERVER_NAME_BYTES.length))) {
            return -1;
        }
        final byte[] addr = address.getAddress().getAddress();
        if (!Arrays.equals(addr, Arrays.copyOfRange(bytes, SERVER_NAME_BYTES.length,
                addr.length + SERVER_NAME_BYTES.length))) {
            return -1;
        }
        return minLength;
    }

    @Override
    public int maxTokenLength() {
        return MAX_TOKEN_LEN;
    }
}
