curl -v -X PUT -u admin:admin --header "Content-Type:application/json"  --data @ietfmud.json  http://$CONTROLLER_ADDR:8181/restconf/config/ietf-mud:mud
sudo ovs-ofctl dump-flows s1 -O openflow13
