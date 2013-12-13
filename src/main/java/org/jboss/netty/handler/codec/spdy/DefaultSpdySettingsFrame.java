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
package org.jboss.netty.handler.codec.spdy;

import org.jboss.netty.util.internal.StringUtil;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The default {@link SpdySettingsFrame} implementation.
 */
public class DefaultSpdySettingsFrame implements SpdySettingsFrame {

    private boolean clear;
    private final Map<Integer, Setting> settingsMap = new TreeMap<Integer, Setting>();

    public Set<Integer> getIds() {
        return settingsMap.keySet();
    }

    public boolean isSet(int id) {
        Integer key = id;
        return settingsMap.containsKey(key);
    }

    public int getValue(int id) {
        Integer key = id;
        if (settingsMap.containsKey(key)) {
            return settingsMap.get(key).getValue();
        } else {
            return -1;
        }
    }

    public void setValue(int id, int value) {
        setValue(id, value, false, false);
    }

    public void setValue(int id, int value, boolean persistValue, boolean persisted) {
        if (id < 0 || id > SpdyCodecUtil.SPDY_SETTINGS_MAX_ID) {
            throw new IllegalArgumentException("Setting ID is not valid: " + id);
        }
        Integer key = id;
        if (settingsMap.containsKey(key)) {
            Setting setting = settingsMap.get(key);
            setting.setValue(value);
            setting.setPersist(persistValue);
            setting.setPersisted(persisted);
        } else {
            settingsMap.put(key, new Setting(value, persistValue, persisted));
        }
    }

    public void removeValue(int id) {
        Integer key = id;
        if (settingsMap.containsKey(key)) {
            settingsMap.remove(key);
        }
    }

    public boolean isPersistValue(int id) {
        Integer key = id;
        if (settingsMap.containsKey(key)) {
            return settingsMap.get(key).isPersist();
        } else {
            return false;
        }
    }

    public void setPersistValue(int id, boolean persistValue) {
        Integer key = id;
        if (settingsMap.containsKey(key)) {
            settingsMap.get(key).setPersist(persistValue);
        }
    }

    public boolean isPersisted(int id) {
        Integer key = id;
        if (settingsMap.containsKey(key)) {
            return settingsMap.get(key).isPersisted();
        } else {
            return false;
        }
    }

    public void setPersisted(int id, boolean persisted) {
        Integer key = id;
        if (settingsMap.containsKey(key)) {
            settingsMap.get(key).setPersisted(persisted);
        }
    }

    public boolean clearPreviouslyPersistedSettings() {
        return clear;
    }

    public void setClearPreviouslyPersistedSettings(boolean clear) {
        this.clear = clear;
    }

    private Set<Map.Entry<Integer, Setting>> getSettings() {
        return settingsMap.entrySet();
    }

    private void appendSettings(StringBuilder buf) {
        for (Map.Entry<Integer, Setting> e: getSettings()) {
            Setting setting = e.getValue();
            buf.append("--> ");
            buf.append(e.getKey().toString());
            buf.append(':');
            buf.append(setting.getValue());
            buf.append(" (persist value: ");
            buf.append(setting.isPersist());
            buf.append("; persisted: ");
            buf.append(setting.isPersisted());
            buf.append(')');
            buf.append(StringUtil.NEWLINE);
        }
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(getClass().getSimpleName());
        buf.append(StringUtil.NEWLINE);
        appendSettings(buf);
        buf.setLength(buf.length() - StringUtil.NEWLINE.length());
        return buf.toString();
    }

    private static final class Setting {

        private int value;
        private boolean persist;
        private boolean persisted;

        Setting(int value, boolean persist, boolean persisted) {
            this.value = value;
            this.persist = persist;
            this.persisted = persisted;
        }

        int getValue() {
            return value;
        }

        void setValue(int value) {
            this.value = value;
        }

        boolean isPersist() {
            return persist;
        }

        void setPersist(boolean persist) {
            this.persist = persist;
        }

        boolean isPersisted() {
            return persisted;
        }

        void setPersisted(boolean persisted) {
            this.persisted = persisted;
        }
    }
}
