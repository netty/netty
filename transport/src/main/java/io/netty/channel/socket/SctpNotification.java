/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.socket;

import com.sun.nio.sctp.Notification;

public final class SctpNotification implements SctpMessage {
    private final Notification notification;
    private final Object attachment;


    public SctpNotification(Notification notification, Object attachment) {
        this.notification = notification;
        this.attachment = attachment;
    }

    /**
     * Return the {@link Notification}
     */
    public Notification notification() {
        return notification;
    }

    /**
     * Return the attachment of this {@link SctpNotification}, or
     * <code>null</code> if no attachment was provided
     */
    public Object attachment() {
        return attachment;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        SctpNotification that = (SctpNotification) o;

        if (!attachment.equals(that.attachment)) {
            return false;
        }

        if (!notification.equals(that.notification)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = notification.hashCode();
        result = 31 * result + attachment.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "SctpNotification{" +
                "notification=" + notification +
                ", attachment=" + attachment +
                '}';
    }
}
