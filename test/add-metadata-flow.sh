sudo ovs-ofctl add-flow s1 table=2,priority=40,metadata=0x523f476a/0xffffffff,actions=push_vlan:0x8100,output:6,goto_table:3 -O openflow13
