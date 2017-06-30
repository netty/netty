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

import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import javax.net.ssl.SSLEngine;


/**
 * The {@link JdkApplicationProtocolNegotiator} to use if you need ALPN and are using {@link SslProvider#JDK}.
 */
public final class JdkAlpnApplicationProtocolNegotiator extends JdkBaseApplicationProtocolNegotiator {
    private static final SslEngineWrapperFactory ALPN_WRAPPER = new AlpnWrapper();

    /**
     * Create a new instance.
     * @param protocols The order of iteration determines the preference of support for protocols.
     */
    public JdkAlpnApplicationProtocolNegotiator(Iterable<String> protocols) {
        this(false, protocols);
    }

    /**
     * Create a new instance.
     * @param protocols The order of iteration determines the preference of support for protocols.
     */
    public JdkAlpnApplicationProtocolNegotiator(String... protocols) {
        this(false, protocols);
    }

    /**
     * Create a new instance.
     * @param failIfNoCommonProtocols Fail with a fatal alert if not common protocols are detected.
     * @param protocols The order of iteration determines the preference of support for protocols.
     */
    public JdkAlpnApplicationProtocolNegotiator(boolean failIfNoCommonProtocols, Iterable<String> protocols) {
        this(failIfNoCommonProtocols, failIfNoCommonProtocols, protocols);
    }

    /**
     * Create a new instance.
     * @param failIfNoCommonProtocols Fail with a fatal alert if not common protocols are detected.
     * @param protocols The order of iteration determines the preference of support for protocols.
     */
    public JdkAlpnApplicationProtocolNegotiator(boolean failIfNoCommonProtocols, String... protocols) {
        this(failIfNoCommonProtocols, failIfNoCommonProtocols, protocols);
    }

    /**
     * Create a new instance.
     * @param clientFailIfNoCommonProtocols Client side fail with a fatal alert if not common protocols are detected.
     * @param serverFailIfNoCommonProtocols Server side fail with a fatal alert if not common protocols are detected.
     * @param protocols The order of iteration determines the preference of support for protocols.
     */
    public JdkAlpnApplicationProtocolNegotiator(boolean clientFailIfNoCommonProtocols,
            boolean serverFailIfNoCommonProtocols, Iterable<String> protocols) {
        this(serverFailIfNoCommonProtocols ? FAIL_SELECTOR_FACTORY : NO_FAIL_SELECTOR_FACTORY,
                clientFailIfNoCommonProtocols ? FAIL_SELECTION_LISTENER_FACTORY : NO_FAIL_SELECTION_LISTENER_FACTORY,
                protocols);
    }

    /**
     * Create a new instance.
     * @param clientFailIfNoCommonProtocols Client side fail with a fatal alert if not common protocols are detected.
     * @param serverFailIfNoCommonProtocols Server side fail with a fatal alert if not common protocols are detected.
     * @param protocols The order of iteration determines the preference of support for protocols.
     */
    public JdkAlpnApplicationProtocolNegotiator(boolean clientFailIfNoCommonProtocols,
            boolean serverFailIfNoCommonProtocols, String... protocols) {
        this(serverFailIfNoCommonProtocols ? FAIL_SELECTOR_FACTORY : NO_FAIL_SELECTOR_FACTORY,
                clientFailIfNoCommonProtocols ? FAIL_SELECTION_LISTENER_FACTORY : NO_FAIL_SELECTION_LISTENER_FACTORY,
                protocols);
    }

    /**
     * Create a new instance.
     * @param selectorFactory The factory which provides classes responsible for selecting the protocol.
     * @param listenerFactory The factory which provides to be notified of which protocol was selected.
     * @param protocols The order of iteration determines the preference of support for protocols.
     */
    public JdkAlpnApplicationProtocolNegotiator(ProtocolSelectorFactory selectorFactory,
            ProtocolSelectionListenerFactory listenerFactory, Iterable<String> protocols) {
        super(ALPN_WRAPPER, selectorFactory, listenerFactory, protocols);
    }

    /**
     * Create a new instance.
     * @param selectorFactory The factory which provides classes responsible for selecting the protocol.
     * @param listenerFactory The factory which provides to be notified of which protocol was selected.
     * @param protocols The order of iteration determines the preference of support for protocols.
     */
    public JdkAlpnApplicationProtocolNegotiator(ProtocolSelectorFactory selectorFactory,
            ProtocolSelectionListenerFactory listenerFactory, String... protocols) {
        super(ALPN_WRAPPER, selectorFactory, listenerFactory, protocols);
    }

    private static final class AlpnWrapper implements SslEngineWrapperFactory {
      private final InternalLogger logger = InternalLoggerFactory.getInstance(getClass());

      private final Method conscryptIsEngineSupported;
      private final Method conscryptNewServerEngine;
      private final Method conscryptNewClientEngine;

      private final Method jettyNewServerEngine;
      private final Method jettyNewClientEngine;

      private AlpnWrapper() {
          Exception cause = null;
          Method conscryptIsEngineSupported = null;
          Method conscryptNewServerEngine = null;
          Method conscryptNewClientEngine = null;
          boolean conscryptAvailable = false;
          try {
              Class<?> clz = Class.forName("io.netty.handler.ssl.ConscryptAlpnSslEngine");
              Method isAvailable = clz.getDeclaredMethod("isAvailable");
              if ((Boolean) isAvailable.invoke(null)) {
                  conscryptIsEngineSupported = clz.getDeclaredMethod("isEngineSupported", SSLEngine.class);
                  conscryptNewServerEngine = clz.getDeclaredMethod(
                          "newServerEngine", SSLEngine.class, JdkApplicationProtocolNegotiator.class);
                  conscryptNewClientEngine = clz.getDeclaredMethod(
                          "newClientEngine", SSLEngine.class, JdkApplicationProtocolNegotiator.class);
                  conscryptAvailable = true;
              }
          } catch (Exception e) {
              cause = e;
          }
          this.conscryptIsEngineSupported = conscryptIsEngineSupported;
          this.conscryptNewServerEngine = conscryptNewServerEngine;
          this.conscryptNewClientEngine = conscryptNewClientEngine;
          if (!conscryptAvailable) {
              logger.debug("Unable to load Conscrypt ALPN", cause);
          }

          cause = null;
          Method jettyNewServerEngine = null;
          Method jettyNewClientEngine = null;
          boolean jettyAvailable = false;
          try {
              Class<?> clz = Class.forName("io.netty.handler.ssl.JettyAlpnSslEngine");
              Method isAvailable = clz.getDeclaredMethod("isAvailable");
              if ((Boolean) isAvailable.invoke(null)) {
                  jettyNewServerEngine = clz.getDeclaredMethod(
                          "newServerEngine", SSLEngine.class, JdkApplicationProtocolNegotiator.class);
                  jettyNewClientEngine = clz.getDeclaredMethod(
                          "newClientEngine", SSLEngine.class, JdkApplicationProtocolNegotiator.class);
                  jettyAvailable = true;
              }
          } catch (Exception e) {
              cause = e;
          }
          if (!jettyAvailable) {
              logger.debug("Unable to load Jetty ALPN", cause);
          }
          if (!conscryptAvailable && !jettyAvailable) {
              logger.info("Unable to load Conscrypt ALPN or Jetty ALPN");
          }
          this.jettyNewServerEngine = jettyNewServerEngine;
          this.jettyNewClientEngine = jettyNewClientEngine;
      }

        @Override
        public SSLEngine wrapSslEngine(SSLEngine engine, JdkApplicationProtocolNegotiator applicationNegotiator,
                                       boolean isServer) {
            if (conscryptIsEngineSupported == null && jettyNewServerEngine == null) {
                throw new RuntimeException("ALPN unsupported. Is your classpath configured correctly?"
                        + " For Conscrypt, add the appropriate Conscrypt JAR to classpath and set the security"
                        + " provider. For Jetty-ALPN, see "
                        + "http://www.eclipse.org/jetty/documentation/current/alpn-chapter.html#alpn-starting");
            }
            try {
                if (conscryptIsEngineSupported != null && (Boolean) conscryptIsEngineSupported.invoke(null, engine)) {
                    return (SSLEngine) (isServer
                            ? conscryptNewServerEngine.invoke(null, engine, applicationNegotiator)
                            : conscryptNewClientEngine.invoke(null, engine, applicationNegotiator));
                }
                if (jettyNewServerEngine != null) {
                    return (SSLEngine) (isServer
                            ? jettyNewServerEngine.invoke(null, engine, applicationNegotiator)
                            : jettyNewClientEngine.invoke(null, engine, applicationNegotiator));
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e);
            }
            throw new RuntimeException("Unable to wrap SSLEngine of type " + engine.getClass().getName());
        }
    }
}
