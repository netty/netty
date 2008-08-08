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


public class FailedChannelFuture extends CompleteChannelFuture {

    private final Throwable cause;

    public FailedChannelFuture(Channel channel, Throwable cause) {
        super(channel);
        if (cause == null) {
            throw new NullPointerException("cause");
        }
        this.cause = cause;
    }

    public Throwable getCause() {
        return cause;
    }

    public boolean isSuccess() {
        return false;
    }
}
