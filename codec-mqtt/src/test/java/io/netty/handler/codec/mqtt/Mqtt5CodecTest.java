package io.netty.handler.codec.mqtt;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.CharsetUtil;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.LinkedList;
import java.util.List;

import static io.netty.handler.codec.mqtt.MqttCodecTest.*;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for MqttEncoder and MqttDecoder for MQTT v5.
 */
public class Mqtt5CodecTest {

    private static final String CLIENT_ID = "RANDOM_TEST_CLIENT";
    private static final String WILL_TOPIC = "/my_will";
    private static final byte[] WILL_MESSAGE = "gone".getBytes(CharsetUtil.UTF_8);
    private static final String USER_NAME = "happy_user";
    private static final String PASSWORD = "123_or_no_pwd";

    private static final int KEEP_ALIVE_SECONDS = 600;

    private static final ByteBufAllocator ALLOCATOR = new UnpooledByteBufAllocator(false);

    @Mock
    private final ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);

    @Mock
    private final Channel channel = mock(Channel.class);

    private final MqttDecoder mqttDecoder = new MqttDecoder(new VariableHeaderDecoderV5());

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(ctx.channel()).thenReturn(channel);
    }

    @Test
    public void testConnectMessageForMqtt5() throws Exception {
        MqttProperties props = new MqttProperties();
        props.add(new MqttProperties.IntegerProperty(0x11, 10)); //session expiry interval
        final MqttConnectMessage message = createConnectV5Message(props);
        ByteBuf byteBuf = MqttEncoderV5.doEncode(ALLOCATOR, message);

        final List<Object> out = new LinkedList<Object>();
        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object but got " + out.size(), 1, out.size());

        final MqttConnectMessage decodedMessage = (MqttConnectMessage) out.get(0);

        validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
        validateConnectVariableHeader(message.variableHeader(), decodedMessage.variableHeader());
        validateConnectPayload(message.payload(), decodedMessage.payload());
    }

    @Test
    public void testConnAckMessage() throws Exception {
        MqttProperties props = new MqttProperties();
        props.add(new MqttProperties.IntegerProperty(0x11, 10)); //session expiry interval
        final MqttConnAckMessage message = createConnAckMessage(props);
        ByteBuf byteBuf = MqttEncoderV5.doEncode(ALLOCATOR, message);

        final List<Object> out = new LinkedList<Object>();

        mqttDecoder.decode(ctx, byteBuf, out);

        assertEquals("Expected one object but got " + out.size(), 1, out.size());

        final MqttConnAckMessage decodedMessage = (MqttConnAckMessage) out.get(0);
        validateFixedHeaders(message.fixedHeader(), decodedMessage.fixedHeader());
        validateConnAckVariableHeader(message.variableHeader(), decodedMessage.variableHeader());

        final MqttProperties expected = message.variableHeader().properties();
        final MqttProperties actual = decodedMessage.variableHeader().properties();
        assertEquals(expected.listAll().iterator().next().value, actual.listAll().iterator().next().value);
    }

    private static MqttConnectMessage createConnectV5Message(MqttProperties properties) {
        return createConnectV5Message(USER_NAME, PASSWORD, properties);
    }

    private static MqttConnectMessage createConnectV5Message(String username, String password, MqttProperties properties) {
        return MqttMessageBuilders.connect()
                .clientId(CLIENT_ID)
                .protocolVersion(MqttVersion.MQTT_5)
                .username(username)
                .password(password.getBytes(CharsetUtil.UTF_8))
                .willRetain(true)
                .willQoS(MqttQoS.AT_LEAST_ONCE)
                .willFlag(true)
                .willTopic(WILL_TOPIC)
                .willMessage(WILL_MESSAGE)
                .cleanSession(true)
                .keepAlive(KEEP_ALIVE_SECONDS)
                .properties(properties)
                .build();
    }

    private static MqttConnAckMessage createConnAckMessage(MqttProperties properties) {
        return MqttMessageBuilders.connAck()
                .returnCode(MqttConnectReturnCode.CONNECTION_ACCEPTED)
                .sessionPresent(true)
                .properties(properties)
                .build();
    }
}
