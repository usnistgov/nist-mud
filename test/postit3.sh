curl -v -X PUT -u admin:admin --header "Content-Type:application/json"  --data @ids-config.json  http://$CONTROLLER_ADDR:8181/restconf/config/nist-flowmon-config:flowmon-config
