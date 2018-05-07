curl -v -X PUT -u admin:admin --header "Content-Type:application/json"  --data @genius2.json  http://$CONTROLLER_ADDR:8181/restconf/config/ietf-interfaces:interfaces/interface/member-interface-1
