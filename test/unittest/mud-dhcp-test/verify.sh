openssl x509 -in mudsigner.crt  -pubkey -noout > mudsigner.pubkey
openssl dgst -sha256 -verify mudsigner.pubkey -signature mudfile.json.sha256 mudfile.json
