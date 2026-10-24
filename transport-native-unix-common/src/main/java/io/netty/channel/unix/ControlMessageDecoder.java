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
package io.netty.channel.unix;

import io.netty.channel.ChannelException;
import io.netty.util.internal.ObjectUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Decodes the control messages that are received via {@code recvmsg(...)} into {@link ControlMessages}. Each instance
 * decodes a fixed set of {@link ControlMessage}s, which were requested via
 * {@link UnixChannelOption#RECEIVE_CONTROL_MESSAGES}. Received control messages which are not part of this set are
 * ignored.
 * <p>
 * <strong>Internal usage only!</strong>
 */
public final class ControlMessageDecoder {

    private final ControlMessageLayout layout;
    private final ControlMessage<?>[] types;
    private final Set<ControlMessage<?>> typeSet;
    private final int controlLength;

    ControlMessageDecoder(ControlMessageLayout layout, Collection<? extends ControlMessage<?>> types) {
        this.layout = layout;
        this.types = types.toArray(new ControlMessage<?>[0]);
        this.typeSet = Collections.unmodifiableSet(new LinkedHashSet<ControlMessage<?>>(types));
        int controlLength = 0;
        for (ControlMessage<?> type : this.types) {
            controlLength += layout.space(type.maxDataLength());
        }
        this.controlLength = controlLength;
    }

    /**
     * Update the reception of control messages for the given {@link Socket}, so only the given
     * {@link ControlMessage}s are received.
     *
     * @param socket    the {@link Socket}.
     * @param current   the {@link ControlMessageDecoder} that was used before or {@code null} if none.
     * @param types     the {@link ControlMessage}s that should be received.
     * @return          the {@link ControlMessageDecoder} that should be used from now on or {@code null} if no
     *                  control messages should be received.
     * @throws UnsupportedOperationException if one of the {@link ControlMessage}s can't be received on this platform.
     * @throws ChannelException if the reception of a control message could not be enabled or disabled.
     * @throws IOException if the socket was closed.
     */
    public static ControlMessageDecoder update(Socket socket, ControlMessageDecoder current,
                                               Collection<? extends ControlMessage<?>> types) throws IOException {
        ObjectUtil.checkNotNull(socket, "socket");
        ObjectUtil.checkNotNull(types, "types");
        Set<ControlMessage<?>> newTypes = new LinkedHashSet<ControlMessage<?>>(types.size());
        for (ControlMessage<?> type : types) {
            ObjectUtil.checkNotNull(type, "type");
            if (!type.isReceiveSupported()) {
                throw new UnsupportedOperationException("Receiving " + type + " is not supported on this platform");
            }
            newTypes.add(type);
        }
        Set<ControlMessage<?>> oldTypes = types(current);
        // First enable the new ones, so we can restore the previous state if this fails.
        List<ControlMessage<?>> enabled = new ArrayList<ControlMessage<?>>(newTypes.size());
        try {
            for (ControlMessage<?> type : newTypes) {
                if (!oldTypes.contains(type)) {
                    type.setReceiveEnabled(socket, true);
                    enabled.add(type);
                }
            }
        } catch (IOException | RuntimeException e) {
            // Try to restore the previous state.
            for (ControlMessage<?> type : enabled) {
                try {
                    type.setReceiveEnabled(socket, false);
                } catch (Throwable cause) {
                    e.addSuppressed(cause);
                }
            }
            throw e;
        }
        // Now disable the ones that are not needed anymore. This can only fail if the socket was closed in the
        // meantime, as we were able to enable these before.
        for (ControlMessage<?> type : oldTypes) {
            if (!newTypes.contains(type)) {
                type.setReceiveEnabled(socket, false);
            }
        }
        return newTypes.isEmpty() ? null : new ControlMessageDecoder(ControlMessageConstants.LAYOUT, newTypes);
    }

    /**
     * Returns the {@link ControlMessage}s that are received via the given {@link ControlMessageDecoder}.
     *
     * @param decoder the {@link ControlMessageDecoder} or {@code null} if no control messages are received.
     * @return the unmodifiable {@link Set} of {@link ControlMessage}s.
     */
    public static Set<ControlMessage<?>> types(ControlMessageDecoder decoder) {
        return decoder == null ? Collections.<ControlMessage<?>>emptySet() : decoder.typeSet;
    }

    /**
     * Returns the number of bytes that should be reserved to receive the control messages of a single datagram.
     */
    public int controlLength() {
        return controlLength;
    }

    /**
     * Decode the received control messages.
     *
     * @param buffer    the direct {@link ByteBuffer} which contains the control messages and uses the native byte
     *                  order.
     * @param offset    the offset of the control messages.
     * @param length    the length of the control messages (which is {@code msg_controllen} after
     *                  {@code recvmsg(...)} returned).
     * @return          the decoded {@link ControlMessages}.
     */
    public ControlMessages decode(ByteBuffer buffer, int offset, int length) {
        Object[] values = null;
        int count = 0;
        final int end = offset + length;
        final int dataOffset = layout.dataOffset();
        int cmsg = offset;
        // See CMSG_FIRSTHDR(...) and CMSG_NXTHDR(...)
        while (cmsg + layout.headerSize() <= end) {
            long cmsgLen = layout.cmsgLen(buffer, cmsg);
            if (cmsgLen < dataOffset || cmsgLen > end - cmsg) {
                // Either malformed or truncated (which can happen if MSG_CTRUNC is set).
                break;
            }
            int level = layout.cmsgLevel(buffer, cmsg);
            int type = layout.cmsgType(buffer, cmsg);
            for (int i = 0; i < types.length; i++) {
                if (types[i].isReceived(level, type)) {
                    Object value = types[i].decode(buffer, cmsg + dataOffset, (int) cmsgLen - dataOffset);
                    if (value != null) {
                        if (values == null) {
                            values = new Object[types.length];
                        }
                        if (values[i] == null) {
                            count++;
                        }
                        values[i] = value;
                    }
                }
            }
            cmsg += layout.align((int) cmsgLen);
        }
        if (count == 0) {
            return ControlMessages.EMPTY;
        }
        if (count == types.length) {
            // All the requested control messages were received, which is the common case. In this case we can use
            // the arrays directly, the types are never modified.
            return new ControlMessages(types, values);
        }
        ControlMessage<?>[] decodedTypes = new ControlMessage<?>[count];
        Object[] decodedValues = new Object[count];
        for (int i = 0, j = 0; j < count; i++) {
            if (values[i] != null) {
                decodedTypes[j] = types[i];
                decodedValues[j] = values[i];
                j++;
            }
        }
        return new ControlMessages(decodedTypes, decodedValues);
    }
}
