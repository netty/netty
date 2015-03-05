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
package io.netty.handler.ssl;

import javax.net.ssl.SSLEngine;
import java.util.Collections;
import java.util.List;

import static io.netty.handler.ssl.ApplicationProtocolUtil.*;
import static io.netty.util.internal.ObjectUtil.*;

/**
 * Provides an {@link SSLEngine} agnostic way to configure a {@link ApplicationProtocolNegotiator}.
 */
public final class ApplicationProtocolConfig {

    /**
     * The configuration that disables application protocol negotiation.
     */
    public static final ApplicationProtocolConfig DISABLED = new ApplicationProtocolConfig();

    private final List<String> supportedProtocols;
    private final Protocol protocol;
    private final SelectorFailureBehavior selectorBehavior;
    private final SelectedListenerFailureBehavior selectedBehavior;

    /**
     * Create a new instance.
     * @param protocol The application protocol functionality to use.
     * @param selectorBehavior How the peer selecting the protocol should behave.
     * @param selectedBehavior How the peer being notified of the selected protocol should behave.
     * @param supportedProtocols The order of iteration determines the preference of support for protocols.
     */
    public ApplicationProtocolConfig(Protocol protocol, SelectorFailureBehavior selectorBehavior,
            SelectedListenerFailureBehavior selectedBehavior, Iterable<String> supportedProtocols) {
        this(protocol, selectorBehavior, selectedBehavior, toList(supportedProtocols));
    }

    /**
     * Create a new instance.
     * @param protocol The application protocol functionality to use.
     * @param selectorBehavior How the peer selecting the protocol should behave.
     * @param selectedBehavior How the peer being notified of the selected protocol should behave.
     * @param supportedProtocols The order of iteration determines the preference of support for protocols.
     */
    public ApplicationProtocolConfig(Protocol protocol, SelectorFailureBehavior selectorBehavior,
            SelectedListenerFailureBehavior selectedBehavior, String... supportedProtocols) {
        this(protocol, selectorBehavior, selectedBehavior, toList(supportedProtocols));
    }

    /**
     * Create a new instance.
     * @param protocol The application protocol functionality to use.
     * @param selectorBehavior How the peer selecting the protocol should behave.
     * @param selectedBehavior How the peer being notified of the selected protocol should behave.
     * @param supportedProtocols The order of iteration determines the preference of support for protocols.
     */
    private ApplicationProtocolConfig(
            Protocol protocol, SelectorFailureBehavior selectorBehavior,
            SelectedListenerFailureBehavior selectedBehavior, List<String> supportedProtocols) {
        this.supportedProtocols = Collections.unmodifiableList(checkNotNull(supportedProtocols, "supportedProtocols"));
        this.protocol = checkNotNull(protocol, "protocol");
        this.selectorBehavior = checkNotNull(selectorBehavior, "selectorBehavior");
        this.selectedBehavior = checkNotNull(selectedBehavior, "selectedBehavior");

        if (protocol == Protocol.NONE) {
            throw new IllegalArgumentException("protocol (" + Protocol.NONE + ") must not be " + Protocol.NONE + '.');
        }
        if (supportedProtocols.isEmpty()) {
            throw new IllegalArgumentException("supportedProtocols must be not empty");
        }
    }

    /**
     * A special constructor that is used to instantiate {@link #DISABLED}.
     */
    private ApplicationProtocolConfig() {
        supportedProtocols = Collections.emptyList();
        protocol = Protocol.NONE;
        selectorBehavior = SelectorFailureBehavior.CHOOSE_MY_LAST_PROTOCOL;
        selectedBehavior = SelectedListenerFailureBehavior.ACCEPT;
    }

    /**
     * Defines which application level protocol negotiation to use.
     */
    public enum Protocol {
        NONE, NPN, ALPN, NPN_AND_ALPN
    }

    /**
     * Defines the most common behaviors for the peer that selects the application protocol.
     */
    public enum SelectorFailureBehavior {
        FATAL_ALERT, NO_ADVERTISE, CHOOSE_MY_LAST_PROTOCOL
    }

    /**
     * Defines the most common behaviors for the peer which is notified of the selected protocol.
     */
    public enum SelectedListenerFailureBehavior {
        ACCEPT, FATAL_ALERT, CHOOSE_MY_LAST_PROTOCOL
    }

    /**
     * The application level protocols supported.
     */
    public List<String> supportedProtocols() {
        return supportedProtocols;
    }

    /**
     * Get which application level protocol negotiation to use.
     */
    public Protocol protocol() {
        return protocol;
    }

    /**
     * Get the desired behavior for the peer who selects the application protocol.
     */
    public SelectorFailureBehavior selectorFailureBehavior() {
        return selectorBehavior;
    }

    /**
     * Get the desired behavior for the peer who is notified of the selected protocol.
     */
    public SelectedListenerFailureBehavior selectedListenerFailureBehavior() {
        return selectedBehavior;
    }
}
