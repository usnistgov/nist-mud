# Generate self signed CA cert.
CACRT=ca.crt
CAKEY=ca.key
MANUFACTURER_CRT=mudsigner.crt
MANUFACTURER_CSR=manufacturer.csr
MANUFACTURER_KEY=mudsigner.key
MANUFACTURER_ALIAS=mudsigner.nist.local
echo "JAVA_HOME is set to "
echo $JAVA_HOME
echo "**************************"

# generate CACRT
openssl req -nodes -new -x509 -sha256 -newkey rsa:4096 -keyout $CAKEY -out $CACRT -days 3560 -subj "/C=US/ST=Maryland/L=Gaithersburg/O=NIST/OU=ITL/CN=cacert" 
#sudo cp ca.crt /usr/local/share/ca-certificates/ca-dhcptest.crt
#sudo update-ca-certificates

# generate manufacturer key
openssl genrsa -out $MANUFACTURER_KEY 2048
# generate manufacturer CSR
openssl req -new -key $MANUFACTURER_KEY -out $MANUFACTURER_CSR -subj "/C=US/ST=Maryland/L=Gaithersburg/O=NIST/OU=ITL/CN=manufacturer" 
# sign the manufacturer CSR
openssl x509 -req -in $MANUFACTURER_CSR -CA $CACRT -CAkey $CAKEY -CAcreateserial -out $MANUFACTURER_CRT


MANUFACTURER_SIGNATURE=mudfile-sensor.p7s
MUDFILE=mudfile-sensor.json
openssl cms -sign -signer $MANUFACTURER_CRT -inkey $MANUFACTURER_KEY -in $MUDFILE -binary -noattr -certfile $CACRT -outform DER  -out $MANUFACTURER_SIGNATURE
openssl cms -verify -binary  -in $MANUFACTURER_SIGNATURE  -signer $MANUFACTURER_CRT -CAfile $CACRT -inform DER  -content $MUDFILE
MANUFACTURER_SIGNATURE=mudfile-otherman.p7s
MUDFILE=mudfile-otherman.json
openssl cms -sign -signer $MANUFACTURER_CRT -inkey $MANUFACTURER_KEY -in $MUDFILE -binary -noattr -certfile $CACRT -outform DER  -out $MANUFACTURER_SIGNATURE
openssl cms -verify -binary  -in $MANUFACTURER_SIGNATURE  -signer $MANUFACTURER_CRT -CAfile $CACRT -inform DER  -content $MUDFILE


# import the CA cert in the JDK keystore
sudo -E $JAVA_HOME/bin/keytool -delete -alias $MANUFACTURER_ALIAS -keystore $JAVA_HOME/jre/lib/security/cacerts -storepass changeit
sudo -E $JAVA_HOME/bin/keytool -importcert -file $CACRT -alias $MANUFACTURER_ALIAS -keystore $JAVA_HOME/jre/lib/security/cacerts -storepass changeit

