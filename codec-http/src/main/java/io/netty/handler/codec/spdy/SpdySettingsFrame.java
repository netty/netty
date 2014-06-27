/*
 * Copyright 2013 The Netty Project
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
package io.netty.handler.codec.spdy;

import java.util.Set;

/**
 * A SPDY Protocol SETTINGS Frame
 */
public interface SpdySettingsFrame extends SpdyFrame {

    int SETTINGS_MINOR_VERSION                  = 0;
    int SETTINGS_UPLOAD_BANDWIDTH               = 1;
    int SETTINGS_DOWNLOAD_BANDWIDTH             = 2;
    int SETTINGS_ROUND_TRIP_TIME                = 3;
    int SETTINGS_MAX_CONCURRENT_STREAMS         = 4;
    int SETTINGS_CURRENT_CWND                   = 5;
    int SETTINGS_DOWNLOAD_RETRANS_RATE          = 6;
    int SETTINGS_INITIAL_WINDOW_SIZE            = 7;
    int SETTINGS_CLIENT_CERTIFICATE_VECTOR_SIZE = 8;

    /**
     * Returns a {@code Set} of the setting IDs.
     * The set's iterator will return the IDs in ascending order.
     */
    Set<Integer> ids();

    /**
     * Returns {@code true} if the setting ID has a value.
     */
    boolean isSet(int id);

    /**
     * Returns the value of the setting ID.
     * Returns -1 if the setting ID is not set.
     */
    int getValue(int id);

    /**
     * Sets the value of the setting ID.
     * The ID cannot be negative and cannot exceed 16777215.
     */
    SpdySettingsFrame setValue(int id, int value);

    /**
     * Sets the value of the setting ID.
     * Sets if the setting should be persisted (should only be set by the server).
     * Sets if the setting is persisted (should only be set by the client).
     * The ID cannot be negative and cannot exceed 16777215.
     */
    SpdySettingsFrame setValue(int id, int value, boolean persistVal, boolean persisted);

    /**
     * Removes the value of the setting ID.
     * Removes all persistence information for the setting.
     */
    SpdySettingsFrame removeValue(int id);

    /**
     * Returns {@code true} if this setting should be persisted.
     * Returns {@code false} if this setting should not be persisted
     *         or if the setting ID has no value.
     */
    boolean isPersistValue(int id);

    /**
     * Sets if this setting should be persisted.
     * Has no effect if the setting ID has no value.
     */
    SpdySettingsFrame setPersistValue(int id, boolean persistValue);

    /**
     * Returns {@code true} if this setting is persisted.
     * Returns {@code false} if this setting should not be persisted
     *         or if the setting ID has no value.
     */
    boolean isPersisted(int id);

    /**
     * Sets if this setting is persisted.
     * Has no effect if the setting ID has no value.
     */
    SpdySettingsFrame setPersisted(int id, boolean persisted);

    /**
     * Returns {@code true} if previously persisted settings should be cleared.
     */
    boolean clearPreviouslyPersistedSettings();

    /**
     * Sets if previously persisted settings should be cleared.
     */
    SpdySettingsFrame setClearPreviouslyPersistedSettings(boolean clear);
}
