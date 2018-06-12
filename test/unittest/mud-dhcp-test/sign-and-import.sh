openssl req -nodes -x509 -sha256 -newkey rsa:4096 -keyout "mudsigner.key" -out "mudsigner.crt" -days 3560 -subj "/C=US/ST=Maryland/L=Gaithersburg/O=NIST/OU=ITL/CN=localhost"
openssl dgst -sha256 -sign "mudsigner.key" -out mudfile.json.sha256 mudfile.json
openssl x509 -in mudsigner.crt  -pubkey -noout > mudsigner.pubkey
sudo keytool -delete -alias toaster.nist.local -keystore $JAVA_HOME/jre/lib/security/cacerts -storepass changeit
sudo keytool -importcert -file mudsigner.crt -alias toaster.nist.local -keystore $JAVA_HOME/jre/lib/security/cacerts -storepass changeit
openssl dgst -sha256 -verify mudsigner.pubkey -signature mudfile.json.sha256 mudfile.json
