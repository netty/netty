/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.mqtt;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;

/**
 * Instantiate the correct decoder for different protocol versions (3.1/3.1.1 or 5)
 */
public class MqttProtocolVersionHandshakeHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(final ChannelHandlerContext ctx, Object msg) {
        MqttConnectMessage connectMsg = (MqttConnectMessage) msg;
        final String handshakerName = ctx.name();
        final ChannelPipeline pipeline = ctx.pipeline();
        if (connectMsg.variableHeader().version() != MqttVersion.MQTT_5.protocolLevel()) {
            // construct MQTT 3.1, 3.1.1 pipeline
            pipeline.addAfter(handshakerName, "decoder", new MqttDecoder(new VariableHeaderDecoderV3(),
                    new MqttMessageFactory()));
            pipeline.addAfter(handshakerName, "encoder", MqttEncoder.INSTANCE);
        } else {
            // construct MQTT 5 pipeline
            pipeline.addAfter(handshakerName, "decoder", new MqttDecoderV5(new VariableHeaderDecoderV5()));
            pipeline.addAfter(handshakerName, "encoder", MqttEncoderV5.INSTANCE);
        }
        pipeline.remove(handshakerName);
    }
}
