# General checks

## Localization
- Avoid the `String.toLowerCase`, `String.toUpperCase`, and similar methods, that don't take a `Locale` argument – these default to the platform locale, which can produce unexpected results

## Performance
- Avoid `String.format`, and other methods that rely on compiling a regular expression on every call
- Cache compiled regular expression `Pattern` objects

## JVM Shared State
- Product code must never modify JVM-wide shared state, such as:
  - `java.security.Security` providers
  - The default `Locale`
  - System properties
  - Setting a `SecurityManager`
  - `Thread.setDefaultUncaughtExceptionHandler`
  - The in/out streams; `System.setIn`/`System.setOut`
  - `java.security.Policy.setPolicy()`
  - `TimeZone.setDefault`
  - `java.net.Authenticator.setDefault`
  - `java.net.CookieHandler.setDefault`
  - `java.net.ProxySelector.setDefault`
  - `java.net.ResponseCache.setDefault`
  - `URL.setURLStreamHandlerFactory`
  - `URLConnection.setContentHandlerFactory`
  - `HttpURLConnection.setFollowRedirects`
  - `javax.net.ssl.SSLContext.setDefault`
  - `HttpsURLConnection.setDefaultHostnameVerifier`
  - `HttpsURLConnection.setDefaultSSLSocketFactory`
  - Never mutate the `ManagementFactory.getPlatformMBeanServer` singleton instance
- Tests that modify any of the above **MUST** be annotated as `@Isolated`
- The isolated tests must be confined to a dedicated test class (the `@Isolated` annotation is class-level only) to minimize the number of isolated tests

## Use existing methods
- New code must use our existing utility methods instead of writing their own
- Most of our utility methods are spread across the following classes:
  - `io.netty.util.internal.PlatformDependent`
  - `io.netty.util.internal.ObjectUtil`
  - `io.netty.util.internal.MathUtil`
  - `io.netty.util.internal.ThrowableUtil`
  - `io.netty.util.internal.StringUtil`
  - `io.netty.util.internal.SystemPropertyUtil`
  - `io.netty.util.NetUtil`
  - `io.netty.util.ReferenceCountUtil`
  - `io.netty.util.NettyRuntime`
  - `io.netty.util.internal.EmptyArrays`
  - `io.netty.buffer.ByteBufUtil`
