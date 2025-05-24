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

import io.netty.buffer.ByteBufUtil;

import java.util.List;

import static io.netty.handler.codec.mqtt.MqttProperties.ASSIGNED_CLIENT_IDENTIFIER;
import static io.netty.handler.codec.mqtt.MqttProperties.AUTHENTICATION_DATA;
import static io.netty.handler.codec.mqtt.MqttProperties.AUTHENTICATION_METHOD;
import static io.netty.handler.codec.mqtt.MqttProperties.CONTENT_TYPE;
import static io.netty.handler.codec.mqtt.MqttProperties.CORRELATION_DATA;
import static io.netty.handler.codec.mqtt.MqttProperties.MAXIMUM_PACKET_SIZE;
import static io.netty.handler.codec.mqtt.MqttProperties.MAXIMUM_QOS;
import static io.netty.handler.codec.mqtt.MqttProperties.PAYLOAD_FORMAT_INDICATOR;
import static io.netty.handler.codec.mqtt.MqttProperties.PUBLICATION_EXPIRY_INTERVAL;
import static io.netty.handler.codec.mqtt.MqttProperties.REASON_STRING;
import static io.netty.handler.codec.mqtt.MqttProperties.RECEIVE_MAXIMUM;
import static io.netty.handler.codec.mqtt.MqttProperties.REQUEST_PROBLEM_INFORMATION;
import static io.netty.handler.codec.mqtt.MqttProperties.REQUEST_RESPONSE_INFORMATION;
import static io.netty.handler.codec.mqtt.MqttProperties.RESPONSE_INFORMATION;
import static io.netty.handler.codec.mqtt.MqttProperties.RESPONSE_TOPIC;
import static io.netty.handler.codec.mqtt.MqttProperties.RETAIN_AVAILABLE;
import static io.netty.handler.codec.mqtt.MqttProperties.SERVER_KEEP_ALIVE;
import static io.netty.handler.codec.mqtt.MqttProperties.SERVER_REFERENCE;
import static io.netty.handler.codec.mqtt.MqttProperties.SESSION_EXPIRY_INTERVAL;
import static io.netty.handler.codec.mqtt.MqttProperties.SHARED_SUBSCRIPTION_AVAILABLE;
import static io.netty.handler.codec.mqtt.MqttProperties.SUBSCRIPTION_IDENTIFIER;
import static io.netty.handler.codec.mqtt.MqttProperties.SUBSCRIPTION_IDENTIFIER_AVAILABLE;
import static io.netty.handler.codec.mqtt.MqttProperties.TOPIC_ALIAS;
import static io.netty.handler.codec.mqtt.MqttProperties.TOPIC_ALIAS_MAXIMUM;
import static io.netty.handler.codec.mqtt.MqttProperties.USER_PROPERTY;
import static io.netty.handler.codec.mqtt.MqttProperties.WILDCARD_SUBSCRIPTION_AVAILABLE;
import static io.netty.handler.codec.mqtt.MqttProperties.WILL_DELAY_INTERVAL;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

public final class MqttTestUtils {
    private MqttTestUtils() {
    }

    public static void validateProperties(MqttProperties expected, MqttProperties actual) {
        for (MqttProperties.MqttProperty expectedProperty : expected.listAll()) {
            int propertyId = expectedProperty.propertyId;
            MqttProperties.MqttProperty actualProperty = actual.getProperty(propertyId);
            List<? extends MqttProperties.MqttProperty> actualProperties =
                    actual.getProperties(propertyId);
            switch (propertyId) {
                // one byte value integer property
                case PAYLOAD_FORMAT_INDICATOR:
                case REQUEST_PROBLEM_INFORMATION:
                case REQUEST_RESPONSE_INFORMATION:
                case MAXIMUM_QOS:
                case RETAIN_AVAILABLE:
                case WILDCARD_SUBSCRIPTION_AVAILABLE:
                case SUBSCRIPTION_IDENTIFIER_AVAILABLE:
                case SHARED_SUBSCRIPTION_AVAILABLE: {
                    final Integer expectedValue = ((MqttProperties.IntegerProperty) expectedProperty).value;
                    final Integer actualValue = ((MqttProperties.IntegerProperty) actualProperty).value;
                    assertEquals(expectedValue, actualValue, "one byte property doesn't match");
                    break;
                }
                // two byte value integer property
                case SERVER_KEEP_ALIVE:
                case RECEIVE_MAXIMUM:
                case TOPIC_ALIAS_MAXIMUM:
                case TOPIC_ALIAS: {
                    final Integer expectedValue = ((MqttProperties.IntegerProperty) expectedProperty).value;
                    final Integer actualValue = ((MqttProperties.IntegerProperty) actualProperty).value;
                    assertEquals(expectedValue, actualValue, "two byte property doesn't match");
                    break;
                }
                // four byte value integer property
                case PUBLICATION_EXPIRY_INTERVAL:
                case SESSION_EXPIRY_INTERVAL:
                case WILL_DELAY_INTERVAL:
                case MAXIMUM_PACKET_SIZE: {
                    final Integer expectedValue = ((MqttProperties.IntegerProperty) expectedProperty).value;
                    final Integer actualValue = ((MqttProperties.IntegerProperty) actualProperty).value;
                    assertEquals(expectedValue, actualValue, "four byte property doesn't match");
                    break;
                }
                // four byte value integer property
                case SUBSCRIPTION_IDENTIFIER: {
                    final Integer expectedValue = ((MqttProperties.IntegerProperty) expectedProperty).value;
                    assertContainsValue("Subscription ID doesn't match", expectedValue, actualProperties);
                    break;
                }
                // UTF-8 string value integer property
                case CONTENT_TYPE:
                case RESPONSE_TOPIC:
                case ASSIGNED_CLIENT_IDENTIFIER:
                case AUTHENTICATION_METHOD:
                case RESPONSE_INFORMATION:
                case SERVER_REFERENCE:
                case REASON_STRING: {
                    final String expectedValue = ((MqttProperties.StringProperty) expectedProperty).value;
                    final String actualValue = ((MqttProperties.StringProperty) actualProperty).value;
                    assertEquals(expectedValue, actualValue, "String property doesn't match");
                    break;
                }
                // User property
                case USER_PROPERTY: {
                    final List<MqttProperties.StringPair> expectedPairs =
                            ((MqttProperties.UserProperties) expectedProperty).value;
                    final List<MqttProperties.StringPair> actualPairs =
                            ((MqttProperties.UserProperties) actualProperty).value;
                    assertEquals(expectedPairs, actualPairs, "User properties count doesn't match");
                    for (int i = 0; i < expectedPairs.size(); i++) {
                        assertEquals(expectedPairs.get(i), actualPairs.get(i), "User property mismatch");
                    }
                    break;
                }
                // byte[] property
                case CORRELATION_DATA:
                case AUTHENTICATION_DATA: {
                    final byte[] expectedValue = ((MqttProperties.BinaryProperty) expectedProperty).value;
                    final byte[] actualValue = ((MqttProperties.BinaryProperty) actualProperty).value;
                    final String expectedHexDump = ByteBufUtil.hexDump(expectedValue);
                    final String actualHexDump = ByteBufUtil.hexDump(actualValue);
                    assertEquals(expectedHexDump, actualHexDump, "byte[] property doesn't match");
                    break;
                }
                default:
                    fail("Property Id not recognized " + Integer.toHexString(propertyId));
            }
        }
        for (MqttProperties.MqttProperty actualProperty : actual.listAll()) {
            MqttProperties.MqttProperty expectedProperty = expected.getProperty(actualProperty.propertyId);
            assertNotNull(expectedProperty, "Property " + actualProperty.propertyId + " not expected");
        }
    }

    private static void assertContainsValue(String message,
                                            Integer expectedValue,
                                            List<? extends MqttProperties.MqttProperty> properties) {
        for (MqttProperties.MqttProperty property: properties) {
            if (property instanceof MqttProperties.IntegerProperty &&
                    ((MqttProperties.IntegerProperty) property).value == expectedValue) {
                return;
            }
        }
        fail(message + " - properties didn't contain expected integer value " + expectedValue + ": " + properties);
    }

    public static void validateSubscribePayload(MqttSubscribePayload expected, MqttSubscribePayload actual) {
        List<MqttTopicSubscription> expectedTopicSubscriptions = expected.topicSubscriptions();
        List<MqttTopicSubscription> actualTopicSubscriptions = actual.topicSubscriptions();

        assertEquals(
                expectedTopicSubscriptions.size(),
                actualTopicSubscriptions.size(),
                "MqttSubscribePayload TopicSubscriptionList size mismatch");
        for (int i = 0; i < expectedTopicSubscriptions.size(); i++) {
            validateTopicSubscription(expectedTopicSubscriptions.get(i), actualTopicSubscriptions.get(i));
        }
    }

    public static void validateTopicSubscription(
            MqttTopicSubscription expected,
            MqttTopicSubscription actual) {
        assertEquals(expected.topicName(), actual.topicName(), "MqttTopicSubscription TopicName mismatch");
        assertEquals(
                expected.qualityOfService(),
                actual.qualityOfService(),
                "MqttTopicSubscription Qos mismatch");
        assertEquals(
                expected.option(),
                actual.option(),
                "MqttTopicSubscription options mismatch");
    }

    public static void validateUnsubscribePayload(MqttUnsubscribePayload expected, MqttUnsubscribePayload actual) {
        assertArrayEquals(
                expected.topics().toArray(),
                actual.topics().toArray(),
                "MqttUnsubscribePayload TopicList mismatch");
    }
}
