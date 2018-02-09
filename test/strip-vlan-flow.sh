sudo ovs-ofctl add-flow s1 table=3,priority=40,vlan_tci=4000,actions=strip_vlan,goto_table:4
