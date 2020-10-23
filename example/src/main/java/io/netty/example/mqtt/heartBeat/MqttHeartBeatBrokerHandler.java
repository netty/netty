/*
 * Copyright 2019 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.example.mqtt.heartBeat;

import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.mqtt.MqttConnAckMessage;
import io.netty.handler.codec.mqtt.MqttConnAckVariableHeader;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.ReferenceCountUtil;

@Sharable
public final class MqttHeartBeatBrokerHandler extends ChannelInboundHandlerAdapter {

    public static final MqttHeartBeatBrokerHandler INSTANCE = new MqttHeartBeatBrokerHandler();

    private MqttHeartBeatBrokerHandler() {
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        MqttMessage mqttMessage = (MqttMessage) msg;
        System.out.println("Received MQTT message: " + mqttMessage);
        switch (mqttMessage.fixedHeader().messageType()) {
        case CONNECT:
            MqttFixedHeader connackFixedHeader =
                    new MqttFixedHeader(MqttMessageType.CONNACK, false, MqttQoS.AT_MOST_ONCE, false, 0);
            MqttConnAckVariableHeader mqttConnAckVariableHeader =
                    new MqttConnAckVariableHeader(MqttConnectReturnCode.CONNECTION_ACCEPTED, false);
            MqttConnAckMessage connack = new MqttConnAckMessage(connackFixedHeader, mqttConnAckVariableHeader);
            ctx.writeAndFlush(connack);
            break;
        case PINGREQ:
            MqttFixedHeader pingreqFixedHeader = new MqttFixedHeader(MqttMessageType.PINGRESP, false,
                                                                     MqttQoS.AT_MOST_ONCE, false, 0);
            MqttMessage pingResp = new MqttMessage(pingreqFixedHeader);
            ctx.writeAndFlush(pingResp);
            break;
        case DISCONNECT:
            ctx.close();
            break;
        default:
            System.out.println("Unexpected message type: " + mqttMessage.fixedHeader().messageType());
            ReferenceCountUtil.release(msg);
            ctx.close();
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        System.out.println("Channel heartBeat lost");
        if (evt instanceof IdleStateEvent && IdleState.READER_IDLE == ((IdleStateEvent) evt).state()) {
            ctx.close();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }
}
