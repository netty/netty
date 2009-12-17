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
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (trustin@gmail.com)
 * @version $Rev$, $Date$
 */
public interface LifeCycleAwareChannelHandler extends ChannelHandler {
    void beforeAdd(ChannelHandlerContext ctx) throws Exception;
    void afterAdd(ChannelHandlerContext ctx) throws Exception;
    void beforeRemove(ChannelHandlerContext ctx) throws Exception;
    void afterRemove(ChannelHandlerContext ctx) throws Exception;
}
