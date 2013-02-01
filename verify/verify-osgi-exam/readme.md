### info

this module build will fail if 
* netty osgi modules can not be loaded by osgi framework
* netty tests fail inside osgi framework

for example
```
ERROR: Bundle io.netty.buffer [15] Error starting mvn:io.netty/netty-buffer/4.0.0.Beta1-SNAPSHOT (org.osgi.framework.BundleException: Unresolved constraint in bundle io.netty.buffer [15]: Unable to resolve 15.0: missing requirement [15.0] osgi.wiring.package; (osgi.wiring.package=io.netty.util.internal))
```
