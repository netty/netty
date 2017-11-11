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
        MqttConnectMessage connectMsg = ((MqttConnectMessage) msg);
        final String handshakerName = ctx.name();
        final ChannelPipeline pipeline = ctx.pipeline();
        if (connectMsg.variableHeader().version() != MqttVersion.MQTT_5.protocolLevel()) {
            // construct MQTT 3.1, 3.1.1 pipeline
            pipeline.addAfter(handshakerName, "decoder", new MqttDecoder(new VariableHeaderDecoderV3(),
                    new MqttMessageFactory()));
            pipeline.addAfter(handshakerName,"encoder", MqttEncoder.INSTANCE);
        } else {
            // construct MQTT 5 pipeline
            pipeline.addAfter(handshakerName, "decoder", new MqttDecoderV5(new VariableHeaderDecoderV5()));
            pipeline.addAfter(handshakerName,"encoder", MqttEncoderV5.INSTANCE);
        }
        pipeline.remove(handshakerName);
    }
}
