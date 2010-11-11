/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.channel;

/**
 * A {@link ChannelHandler} that is notified when it is added to or removed
 * from a {@link ChannelPipeline}.
 *
 * <h3>Invalid access to the {@link ChannelHandlerContext}</h3>
 *
 * Calling {@link ChannelHandlerContext#sendUpstream(ChannelEvent)} or
 * {@link ChannelHandlerContext#sendDownstream(ChannelEvent)} in
 * {@link #beforeAdd(ChannelHandlerContext)} or {@link #afterRemove(ChannelHandlerContext)}
 * might lead to an unexpected behavior.  It is because the context object
 * might not have been fully added to the pipeline or the context object is not
 * a part of the pipeline anymore respectively.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 */
public interface LifeCycleAwareChannelHandler extends ChannelHandler {
    void beforeAdd(ChannelHandlerContext ctx) throws Exception;
    void afterAdd(ChannelHandlerContext ctx) throws Exception;
    void beforeRemove(ChannelHandlerContext ctx) throws Exception;
    void afterRemove(ChannelHandlerContext ctx) throws Exception;
}
