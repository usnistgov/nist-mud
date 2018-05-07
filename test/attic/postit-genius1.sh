curl -v -X PUT -u admin:admin --header "Content-Type:application/json"  --data @genius1.json  http://$CONTROLLER_ADDR:8181/restconf/config/ietf-interfaces:interfaces/
