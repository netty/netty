/*
 * Copyright (C) 2008  Trustin Heuiseimport java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
s of the GNU Lesser General Public
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

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FailedChannelFuture implements ChannelFuture {

    private static final Logger logger = Logger.getLogger(FailedChannelFuture.class.getName());

    private final Channel channel;
    private final Throwable cause;

    public FailedChannelFuture(Channel channel, Throwable cause) {
        if (channel == null) {
            throw new NullPointerException("channel");
        }
        if (cause == null) {
            throw new NullPointerException("cause");
        }
        this.channel = channel;
        this.cause = cause;
    }

    public void addListener(ChannelFutureListener listener) {
        try {
            listener.operationComplete(this);
        } catch (Throwable t) {
            logger.log(
                    Level.WARNING,
                    "An exception was thrown by " +
                    ChannelFutureListener.class.getSimpleName() + ".",
                    t);
        }
    }

    public void removeListener(ChannelFutureListener listener) {
        // NOOP
    }

    public ChannelFuture await() throws InterruptedException {
        return this;
    }

    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        return true;
    }

    public boolean await(long timeoutMillis) throws InterruptedException {
        return true;
    }

    public ChannelFuture awaitUninterruptibly() {
        return this;
    }

    public boolean awaitUninterruptibly(long timeout, TimeUnit unit) {
        return true;
    }

    public boolean awaitUninterruptibly(long timeoutMillis) {
        return true;
    }

    public Throwable getCause() {
        return cause;
    }

    public Channel getChannel() {
        return channel;
    }

    public boolean isDone() {
        return true;
    }

    public boolean isSuccess() {
        return false;
    }

    public void setFailure(Throwable cause) {
        // Unused
    }

    public void setSuccess() {
        // Unused
    }

    public boolean cancel() {
        return false;
    }

    public boolean isCancelled() {
        return false;
    }
}
