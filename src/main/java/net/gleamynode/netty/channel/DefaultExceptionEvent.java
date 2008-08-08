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
package net.gleamynode.netty.channel;


public class DefaultExceptionEvent extends DefaultChannelEvent implements
        ExceptionEvent {

    private final Throwable cause;

    public DefaultExceptionEvent(Channel channel, ChannelFuture future, Throwable cause) {
        super(channel, future);
        if (cause == null) {
            throw new NullPointerException("cause");
        }
        this.cause = cause;
    }

    public Throwable getCause() {
        return cause;
    }

    @Override
    public String toString() {
        return super.toString() + " - (cause: " + cause.getClass().getSimpleName() + ')';
    }
}
