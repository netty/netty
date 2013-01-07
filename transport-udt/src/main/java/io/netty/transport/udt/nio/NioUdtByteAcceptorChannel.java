/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.transport.udt.nio;

import io.netty.buffer.BufType;
import io.netty.buffer.MessageBuf;
import io.netty.channel.ChannelMetadata;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;

import com.barchart.udt.TypeUDT;
import com.barchart.udt.nio.SocketChannelUDT;

/**
 * Byte Channel Acceptor for UDT Streams.
 */
public class NioUdtByteAcceptorChannel extends NioUdtAcceptorChannel {

    private static final InternalLogger logger = InternalLoggerFactory
            .getInstance(NioUdtByteAcceptorChannel.class);

    private static final ChannelMetadata METADATA = new ChannelMetadata(
            BufType.BYTE, false);

    public NioUdtByteAcceptorChannel() {
        super(TypeUDT.STREAM);
    }

    @Override
    protected int doReadMessages(final MessageBuf<Object> buf) throws Exception {
        final SocketChannelUDT channelUDT = javaChannel().accept();
        if (channelUDT == null) {
            return 0;
        } else {
            buf.add(new NioUdtByteConnectorChannel(this, channelUDT.socketUDT()
                    .id(), channelUDT));
            return 1;
        }
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

}
