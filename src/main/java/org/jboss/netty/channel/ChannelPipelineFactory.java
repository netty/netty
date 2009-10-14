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
 * Creates a new {@link ChannelPipeline} for a new {@link Channel}.
 * <p>
 * This interface was introduced to initialize the {@link ChannelPipeline} of
 * the child channel accepted by a {@link ServerChannel}, but it is safe to use
 * it for any other purposes.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @version $Rev$, $Date$
 *
 * @apiviz.has org.jboss.netty.channel.ChannelPipeline oneway - - creates
 */
public interface ChannelPipelineFactory {

    /**
     * Returns a newly created {@link ChannelPipeline}.
     */
    ChannelPipeline getPipeline() throws Exception;
}
