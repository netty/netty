The netty_io_chain.pem file is the cert chain of <https://netty.io>. The file
was created using the browser's export functionality for certs. The cert was
issued by COMODO (via Cloudflare) and its purpose is to demonstrate how to
extract the CA's OCSP responder server URL from the certificate and then
interact with the responder server to check the revocation status of the
certificate. The cert will at some point expire or get revoked. It's probably
a good idea to save it once that happens as it's an excellent example to
demonstrate negative responses from the CA.