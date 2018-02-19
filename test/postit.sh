curl -v -X PUT -u admin:admin --header "Content-Type:application/json"  --data @topology.json  http://$CONTROLLER_ADDR:8181/restconf/config/nist-network-topology:topology
curl -v -X PUT -u admin:admin --header "Content-Type:application/json"  --data @access-control-list.json  http://$CONTROLLER_ADDR:8181/restconf/config/ietf-access-control-list:access-lists
curl -v -X PUT -u admin:admin --header "Content-Type:application/json"  --data @device-association.json  http://$CONTROLLER_ADDR:8181/restconf/config/nist-mud-device-association:mapping
curl -v -X PUT -u admin:admin --header "Content-Type:application/json"  --data @controllerclass-mapping.json  http://$CONTROLLER_ADDR:8181/restconf/config/nist-mud-controllerclass-mapping:controllerclass-mapping
#curl -v -X PUT -u admin:admin --header "Content-Type:application/json"  --data @ietfmud.json  http://$CONTROLLER_ADDR:8181/restconf/config/ietf-mud:mud
#curl -v -X PUT -u admin:admin --header "Content-Type:application/json"  --data @ids-config.json  http://$CONTROLLER_ADDR:8181/restconf/config/nist-ids-config:ids-config-data
#sudo ovs-ofctl dump-flows s1 -O openflow13

