curl -v -X PUT -u admin:admin --header "Content-Type:application/json"  --data @npenodes.json  http://$CONTROLLER_ADDR:8181/restconf/config/nist-network-topology:topology
curl -v -X PUT -u admin:admin --header "Content-Type:application/json"  --data @vlans.json  http://$CONTROLLER_ADDR:8181/restconf/config/nist-network-topology:trunk-switches
