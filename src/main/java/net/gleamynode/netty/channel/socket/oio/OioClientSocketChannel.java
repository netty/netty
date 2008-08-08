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
package net.gleamynode.netty.channel.socket.oio;

import static net.gleamynode.netty.channel.Channels.*;

import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.net.Socket;

import net.gleamynode.netty.channel.ChannelFactory;
import net.gleamynode.netty.channel.ChannelPipeline;
import net.gleamynode.netty.channel.ChannelSink;

class OioClientSocketChannel extends OioSocketChannel {

    volatile PushbackInputStream in;
    volatile OutputStream out;

    OioClientSocketChannel(
            ChannelFactory factory,
            ChannelPipeline pipeline,
            ChannelSink sink) {

        super(null, factory, pipeline, sink, new Socket());

        fireChannelOpen(this);
    }

    @Override
    PushbackInputStream getInputStream() {
        return in;
    }

    @Override
    OutputStream getOutputStream() {
        return out;
    }

    @Override
    void setInputStream(PushbackInputStream in) {
        if (this.in == null) {
            this.in = in;
        } else if (this.in != in) {
            throw new IllegalStateException("Shouldn't reach here.");
        }
    }

    @Override
    void setOutputStream(OutputStream out) {
        if (this.out == null) {
            this.out = out;
        } else if (this.out != out) {
            throw new IllegalStateException("Shouldn't reach here.");
        }
    }
}
