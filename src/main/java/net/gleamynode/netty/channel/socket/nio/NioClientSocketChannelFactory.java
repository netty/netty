/*
 * Copyright (C) 2008  Trustin Heuiseung Lee
 *
import java.util.concurrent.Executor;

import net.gleamynode.netty3.channel.Channel;
import net.gleamynode.netty3.channel.ChannelEvent;
import net.gleamynode.netty3.channel.ChannelFactory;
import net.gleamynode.netty3.pipeline.Pipeline;
import net.gleamynode.netty3.pipeline.PipelineSink;
buted in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, 5th Floor, Boston, MA 02110-1301 USA
 */
package net.gleamynode.netty.channel.socket.nio;

import java.util.concurrent.Executor;

import net.gleamynode.netty.channel.Channel;
import net.gleamynode.netty.channel.ChannelEvent;
import net.gleamynode.netty.channel.ChannelFactory;
import net.gleamynode.netty.pipeline.Pipeline;
import net.gleamynode.netty.pipeline.PipelineSink;

public class NioClientSocketChannelFactory implements ChannelFactory {

    final PipelineSink<ChannelEvent> sink;

    public NioClientSocketChannelFactory(
            Executor bossExecutor, Executor workerExecutor) {
        this(bossExecutor, workerExecutor, Runtime.getRuntime().availableProcessors());
    }

    public NioClientSocketChannelFactory(
            Executor bossExecutor, Executor workerExecutor,
            int workerCount) {
        if (bossExecutor == null) {
            throw new NullPointerException("bossExecutor");
        }
        if (workerExecutor == null) {
            throw new NullPointerException("workerExecutor");
        }
        if (workerCount <= 0) {
            throw new IllegalArgumentException(
                    "workerCount (" + workerCount + ") " +
                    "must be a positive integer.");
        }
        sink = new NioClientSocketPipelineSink(bossExecutor, workerExecutor, workerCount);
    }

    public Channel newChannel(Pipeline<ChannelEvent> pipeline) {
        pipeline.setSink(sink);
        return new NioClientSocketChannel(this, pipeline);
    }

}
