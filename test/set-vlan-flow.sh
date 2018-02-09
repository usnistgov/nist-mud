sudo ovs-ofctl add-flow s1 priority=35,table=2,metadata=1379878762,actions=push_vlan:0x8100,mod_vlan_vid:4000,output:6,goto_table:3 -O openflow13
sudo ovs-ofctl add-flow s1 priority=45,table=3,actions=write_actions:strip_vlan,goto_table:4 -O openflow13
