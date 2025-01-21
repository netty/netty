# Generate CA key and certificate.
export PASS="password"
openssl req -x509 -newkey rsa:2048 -days 3650 -keyout rsapss-ca-key.pem -out rsapss-ca-cert.cert -subj "/C=GB/O=Netty/OU=netty-parent/CN=west.int" -sigopt rsa_padding_mode:pss -sha256 -sigopt rsa_pss_saltlen:20 -passin env:PASS -passout env:PASS

# Generate user key nand.
openssl req -newkey rsa:2048 -keyout rsapss-user-key.pem -out rsaValidation-req.pem -subj "/C=GB/O=Netty/OU=netty-parent/CN=c1" -sigopt rsa_padding_mode:pss -sha256 -sigopt rsa_pss_saltlen:20 -passin env:PASS -passout env:PASS

# Sign user cert request using CA certificate.
openssl x509 -req -in rsaValidation-req.pem -days 3650 -extensions ext -extfile rsapss-signing-ext.txt -CA rsapss-ca-cert.cert -CAkey rsapss-ca-key.pem -CAcreateserial -out rsapss-user-signed.cert -sigopt rsa_padding_mode:pss -sha256 -sigopt rsa_pss_saltlen:20 -passin env:PASS

# Create user certificate keystore.
openssl pkcs12 -export -out rsaValidation-user-certs.p12 -inkey rsapss-user-key.pem -in rsapss-user-signed.cert -passin env:PASS -passout env:PASS

# create keystore for the 
openssl pkcs12 -in rsapss-ca-cert.cert -inkey rsapss-ca-key.pem -passin env:PASS -certfile rsapss-ca-cert.cert -export -out rsaValidations-server-keystore.p12 -passout env:PASS -name localhost

# Create Trustore to verify the EndEntity certificate we have created.
keytool -importcert -storetype PKCS12 -keystore rsaValidations-truststore.p12 -storepass $PASS -alias ca -file rsapss-ca-cert.cert -noprompt

# Clean up files we don't need for the test.
echo "# Cleaning up:"
rm -v rsapss-ca-cert.srl rsapss-ca-key.pem rsapss-user-key.pem rsapss-user-signed.cert rsaValidation-req.pem rsaValidations-truststore.p12