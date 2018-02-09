curl -v -X PUT -u admin:admin --header "Content-Type:application/json"  --data @ids-config.json  http://localhost:8181/restconf/config/nist-ids-config:ids-config-data
echo "************************************************************************"
sudo ovs-ofctl dump-flows s1 -O openflow13
echo "************************************************************************"
sudo ovs-ofctl dump-flows s2 -O openflow13
echo "************************************************************************"
