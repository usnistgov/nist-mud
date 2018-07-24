curl -u admin:admin -v -X POST  -k  --header "Content-Type:application/json" --data @vnf-ready.json http://$CONTROLLER_ADDR:8181/restconf/operations/nist-openstack:vnf-ready
