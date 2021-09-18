/*
 * Copyright 2020 The Netty Project
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

package io.netty.handler.codec.mqtt;


import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.netty.handler.codec.mqtt.MqttProperties.MqttPropertyType.CONTENT_TYPE;
import static io.netty.handler.codec.mqtt.MqttProperties.MqttPropertyType.PAYLOAD_FORMAT_INDICATOR;
import static io.netty.handler.codec.mqtt.MqttProperties.MqttPropertyType.SUBSCRIPTION_IDENTIFIER;
import static io.netty.handler.codec.mqtt.MqttProperties.MqttPropertyType.USER_PROPERTY;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class MqttPropertiesTest {

    private MqttProperties createSampleProperties() {
        MqttProperties props = new MqttProperties();
        props.add(new MqttProperties.IntegerProperty(SUBSCRIPTION_IDENTIFIER.value(), 10));
        props.add(new MqttProperties.IntegerProperty(SUBSCRIPTION_IDENTIFIER.value(), 20));
        props.add(new MqttProperties.IntegerProperty(PAYLOAD_FORMAT_INDICATOR.value(), 6));
        props.add(new MqttProperties.StringProperty(CONTENT_TYPE.value(), "text/plain"));
        props.add(new MqttProperties.UserProperty("isSecret", "true"));
        props.add(new MqttProperties.UserProperty("tag", "firstTag"));
        props.add(new MqttProperties.UserProperty("tag", "secondTag"));
        return props;
    }

    @Test
    public void testGetProperty() {
        MqttProperties props = createSampleProperties();

        assertEquals(
                "text/plain",
                ((MqttProperties.StringProperty) props.getProperty(CONTENT_TYPE.value())).value);
        assertEquals(
                10,
                ((MqttProperties.IntegerProperty) props.getProperty(SUBSCRIPTION_IDENTIFIER.value())).value.intValue());

        List<MqttProperties.StringPair> expectedUserProps = new ArrayList<MqttProperties.StringPair>();
        expectedUserProps.add(new MqttProperties.StringPair("isSecret", "true"));
        expectedUserProps.add(new MqttProperties.StringPair("tag", "firstTag"));
        expectedUserProps.add(new MqttProperties.StringPair("tag", "secondTag"));
        List<MqttProperties.StringPair> actualUserProps =
                ((MqttProperties.UserProperties) props.getProperty(USER_PROPERTY.value())).value;
        assertEquals(expectedUserProps, actualUserProps);
    }

    @Test
    public void testGetProperties() {
        MqttProperties props = createSampleProperties();

        assertEquals(
                Collections.singletonList(new MqttProperties.StringProperty(CONTENT_TYPE.value(), "text/plain")),
                props.getProperties(CONTENT_TYPE.value()));

        List<MqttProperties.IntegerProperty> expectedSubscriptionIds = new ArrayList<MqttProperties.IntegerProperty>();
        expectedSubscriptionIds.add(new MqttProperties.IntegerProperty(SUBSCRIPTION_IDENTIFIER.value(), 10));
        expectedSubscriptionIds.add(new MqttProperties.IntegerProperty(SUBSCRIPTION_IDENTIFIER.value(), 20));
        assertEquals(
                expectedSubscriptionIds,
               props.getProperties(SUBSCRIPTION_IDENTIFIER.value()));

        List<MqttProperties.UserProperty> expectedUserProps = new ArrayList<MqttProperties.UserProperty>();
        expectedUserProps.add(new MqttProperties.UserProperty("isSecret", "true"));
        expectedUserProps.add(new MqttProperties.UserProperty("tag", "firstTag"));
        expectedUserProps.add(new MqttProperties.UserProperty("tag", "secondTag"));
        List<MqttProperties.UserProperty> actualUserProps =
                (List<MqttProperties.UserProperty>) props.getProperties(USER_PROPERTY.value());
        assertEquals(expectedUserProps, actualUserProps);
    }

    @Test
    public void testListAll() {
        MqttProperties props = createSampleProperties();

        List<MqttProperties.MqttProperty> expectedProperties = new ArrayList<MqttProperties.MqttProperty>();
        expectedProperties.add(new MqttProperties.IntegerProperty(PAYLOAD_FORMAT_INDICATOR.value(), 6));
        expectedProperties.add(new MqttProperties.StringProperty(CONTENT_TYPE.value(), "text/plain"));

        expectedProperties.add(new MqttProperties.IntegerProperty(SUBSCRIPTION_IDENTIFIER.value(), 10));
        expectedProperties.add(new MqttProperties.IntegerProperty(SUBSCRIPTION_IDENTIFIER.value(), 20));

        MqttProperties.UserProperties expectedUserProperties = new MqttProperties.UserProperties();
        expectedUserProperties.add(new MqttProperties.StringPair("isSecret", "true"));
        expectedUserProperties.add(new MqttProperties.StringPair("tag", "firstTag"));
        expectedUserProperties.add(new MqttProperties.StringPair("tag", "secondTag"));

        expectedProperties.add(expectedUserProperties);

        assertEquals(expectedProperties, props.listAll());
    }

}
