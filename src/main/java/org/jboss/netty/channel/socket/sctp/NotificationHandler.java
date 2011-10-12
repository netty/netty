package org.jboss.netty.channel.socket.sctp;
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
import com.sun.nio.sctp.*;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;

/**
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://github.com/jestan">Jestan Nirojan</a>
 *
 * @version $Rev$, $Date$
 *
 */

public class NotificationHandler extends AbstractNotificationHandler {

    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(NotificationHandler.class);

    private final SctpChannelImpl sctpChannel;
    private final SctpWorker sctpWorker;

    public NotificationHandler(SctpChannelImpl sctpChannel, SctpWorker sctpWorker) {
        this.sctpChannel = sctpChannel;
        this.sctpWorker = sctpWorker;
    }

    @Override
    public HandlerResult handleNotification(AssociationChangeNotification notification, Object o) {
        fireNotificationReceived(notification, o);
        return HandlerResult.CONTINUE;
    }

    @Override
    public HandlerResult handleNotification(Notification notification, Object o) {
        fireNotificationReceived(notification, o);
        return HandlerResult.CONTINUE;
    }

    @Override
    public HandlerResult handleNotification(PeerAddressChangeNotification notification, Object o) {
        fireNotificationReceived(notification, o);
        return HandlerResult.CONTINUE;
    }

    @Override
    public HandlerResult handleNotification(SendFailedNotification notification, Object o) {
        fireNotificationReceived(notification, o);
        return HandlerResult.CONTINUE;
    }

    @Override
    public HandlerResult handleNotification(ShutdownNotification notification, Object o) {
        sctpWorker.close(sctpChannel, Channels.succeededFuture(sctpChannel));
        return HandlerResult.RETURN;
    }

    private void fireNotificationReceived(Notification notification, Object o) {
        sctpChannel.getPipeline().sendUpstream(new UpstreamChannelNotificationEvent(sctpChannel, notification, o));
    }
}
