/*
 * Copyright (C) 2008  Trustin Heuiseung Lee
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, 5th Floor, Boston, MA 02110-1301 USA
 */
package net.gleamynode.netty.channel.socket.nio;

import java.net.Socket;

import net.gleamynode.netty.channel.socket.DefaultSocketChannelConfig;
import net.gleamynode.netty.util.ConvertUtil;

class DefaultNioSocketChannelConfig extends DefaultSocketChannelConfig
        implements NioSocketChannelConfig {

    private volatile ReceiveBufferSizePredictor predictor =
        new DefaultReceiveBufferSizePredictor();
    private volatile int writeSpinCount = 16;
    private volatile boolean readWriteFair;

    DefaultNioSocketChannelConfig(Socket socket) {
        super(socket);
    }

    @Override
    protected boolean setOption(String key, Object value) {
        if (super.setOption(key, value)) {
            return true;
        }

        if (key.equals("readWriteFair")) {
            setReadWriteFair(ConvertUtil.toBoolean(value));
        } else if (key.equals("writeSpinCount")) {
            setWriteSpinCount(ConvertUtil.toInt(value));
        } else if (key.equals("receiveBufferSizePredictor")) {
            setReceiveBufferSizePredictor((ReceiveBufferSizePredictor) value);
        } else {
            return false;
        }
        return true;
    }

    public int getWriteSpinCount() {
        return writeSpinCount;
    }

    public void setWriteSpinCount(int writeSpinCount) {
        if (writeSpinCount <= 0) {
            throw new IllegalArgumentException(
                    "writeSpinCount must be a positive integer.");
        }
    }

    public ReceiveBufferSizePredictor getReceiveBufferSizePredictor() {
        return predictor;
    }

    public void setReceiveBufferSizePredictor(
            ReceiveBufferSizePredictor predictor) {
        if (predictor == null) {
            throw new NullPointerException("predictor");
        }
        this.predictor = predictor;
    }

    public boolean isReadWriteFair() {
        return readWriteFair;
    }

    public void setReadWriteFair(boolean readWriteFair) {
        this.readWriteFair = readWriteFair;
    }
}
