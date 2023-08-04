# Generate CA key and certificate.
openssl req -x509 -newkey rsa:2048 -days 3650 -keyout rsapss-ca-key.pem -out rsapss-ca-cert.cert -subj "/C=GB/O=Netty/OU=netty-parent/CN=west.int" -sigopt rsa_padding_mode:pss -sha256 -sigopt rsa_pss_saltlen:20

# Generate user key nand.
openssl req -newkey rsa:2048 -keyout rsapss-user-key.pem -out rsaValidation-req.pem -subj "/C=GB/O=Netty/OU=netty-parent/CN=c1" -sigopt rsa_padding_mode:pss -sha256 -sigopt rsa_pss_saltlen:20

# Sign user cert request using CA certificate.
openssl x509 -req -in rsaValidation-req.pem -days 365 -extensions ext -extfile rsapss-signing-ext.txt -CA rsapss-ca-cert.cert -CAkey rsapss-ca-key.pem -CAcreateserial -out rsapss-user-singed.cert -sigopt rsa_padding_mode:pss -sha256 -sigopt rsa_pss_saltlen:20

# Create user certificate keystore.
openssl pkcs12 -export -out rsaValidation-user-certs.p12 -inkey rsapss-user-key.pem -in rsapss-user-singed.cert

# create keystore for the 
openssl pkcs12 -in rsapss-ca-cert.cert -inkey rsapss-ca-key.pem -passin pass:password -certfile rsapss-ca-cert.cert -export -out rsaValidations-server-keystore.p12 -passout pass:password -name localhost


# Create Trustore to verify the EndEntity certificate we have created.
keytool -importcert -storetype PKCS12 -keystore rsaValidations-truststore.p12 -storepass password -alias ca -file rsapss-ca-cert.cert -noprompt
