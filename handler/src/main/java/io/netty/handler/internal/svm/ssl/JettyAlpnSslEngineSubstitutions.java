package io.netty.handler.internal.svm.ssl;

import io.netty.handler.ssl.JdkApplicationProtocolNegotiator;

import javax.net.ssl.SSLEngine;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jdk.JDK8OrEarlier;

@TargetClass(className = "io.netty.handler.ssl.JettyAlpnSslEngine", onlyWith = JDK8OrEarlier.class)
final class JettyAlpnSslEngineSubstitutions {

	private JettyAlpnSslEngineSubstitutions() {
	}

	@Alias
	static boolean isAvailable() {
		return false;
	}

	@Alias
	static JettyAlpnSslEngineSubstitutions newClientEngine(SSLEngine engine,
		JdkApplicationProtocolNegotiator applicationNegotiator) {
		return null;
	}

	@Alias
	static JettyAlpnSslEngineSubstitutions newServerEngine(SSLEngine engine,
		JdkApplicationProtocolNegotiator applicationNegotiator) {
		return null;
	}
}
