curl -u admin:admin -v -X PUT  -k  --header "Content-Type:application/json" --data @openstack-config.json http://$CONTROLLER_ADDR:8181/restconf/config/nist-openstack:openstack-config
curl -u admin:admin -v -X PUT  -k  --header "Content-Type:application/json" --data @openstack-stack-configs.json http://$CONTROLLER_ADDR:8181/restconf/config/nist-openstack:openstack-stacks-config
