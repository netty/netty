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
package net.gleamynode.netty.pipeline;

public class PipelineException extends RuntimeException {

    private static final long serialVersionUID = 2989009630726364799L;

    public PipelineException() {
        super();
    }

    public PipelineException(String message, Throwable cause) {
        super(message, cause);
    }

    public PipelineException(String message) {
        super(message);
    }

    public PipelineException(Throwable cause) {
        super(cause);
    }
}
