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
package net.gleamynode.netty.logging;

/**
 * @author The Netty Project (netty@googlegroups.com)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev$, $Date$
 *
 */
public abstract class Logger {
    public static Logger getLogger(Class<?> clazz) {
        return getLogger(clazz.getName());
    }

    public static Logger getLogger(String name) {
        return LoggerFactory.getDefault().getLogger(name);
    }

    public abstract boolean isDebugEnabled();
    public abstract boolean isInfoEnabled();
    public abstract boolean isWarnEnabled();
    public abstract boolean isErrorEnabled();

    public abstract void debug(String msg);
    public abstract void debug(String msg, Throwable cause);
    public abstract void info(String msg);
    public abstract void info(String msg, Throwable cause);
    public abstract void warn(String msg);
    public abstract void warn(String msg, Throwable cause);
    public abstract void error(String msg);
    public abstract void error(String msg, Throwable cause);
}
