
from mininet.node import OVSController
from mininet.cli import CLI
from mininet.log import setLogLevel
from mininet.net import Mininet
from mininet.node import RemoteController
from mininet.topo import Topo
import pdb
import subprocess
import argparse
import os
import sys
import signal
from distutils.spawn import find_executable
from subprocess import call
from functools import partial
import time
from mininet.node import Host
from mininet.node import Switch
from mininet.log import setLogLevel 



def setupTopology(controller_addr,dns_address, interface):
    "Create and run multiple link network"

    net = Mininet(controller=RemoteController)

    print "mininet created"

    c1 = net.addController('c1', ip=controller_addr,port=6653)


    # h1: IOT Device.
    # h2 : StatciDHCPD
    # h3# : router / NAT
    # h4 : Non IOT device.
	
    h1 = net.addHost('h1')
    h2 = net.addHost('h2')
    h3 = net.addHost('h3')
    h4 = net.addHost('h4')
    h5 = net.addHost('h5')
    h6 = net.addHost('h6')
    h7 = net.addHost('h7')
    h8 = net.addHost('h8')
    h9 = net.addHost('h9')
    h10 = net.addHost('h10')
	

    s1 = net.addSwitch('s1')
    s2 = net.addSwitch('s2')
    s3 = net.addSwitch('s3')
    s4 = net.addSwitch('s4')
    s1.linkTo(s2)
    s2.linkTo(s3)

    s1.linkTo(h1)
    s1.linkTo(h2)
    s1.linkTo(h3)
    s1.linkTo(h4)
    s1.linkTo(h5)
    s1.linkTo(h6)

    s3.linkTo(h7)
    s3.linkTo(h8)
    s3.linkTo(h9)

    s4.linkTo(h7)
    s4.linkTo(h10)




    net.build()
    c1.start()
    s1.start([c1])
    s2.start([c1])
    s3.start([c1])
    s4.start([c1])

    net.start()
     

    # Clean up any traces of the previous invocation (for safety)


    h1.setMAC("00:00:00:00:00:01","h1-eth0")
    h2.setMAC("00:00:00:00:00:02","h2-eth0")
    h3.setMAC("00:00:00:00:00:03","h3-eth0")
    h4.setMAC("00:00:00:00:00:04","h4-eth0")
    h5.setMAC("00:00:00:00:00:05","h5-eth0")
    h6.setMAC("00:00:00:00:00:06","h6-eth0")
    h7.setMAC("00:00:00:00:00:07","h7-eth0")
    h8.setMAC("00:00:00:00:00:08","h8-eth0")
    h9.setMAC("00:00:00:00:00:09","h9-eth0")
    h10.setMAC("00:00:00:00:00:10","h10-eth0")
    
    # Set up a routing rule on h2 to route packets via h3
    h1.cmdPrint('ip route del default')
    h1.cmdPrint('ip route add default via 10.0.0.7 dev h1-eth0')

    # Set up a routing rule on h2 to route packets via h3
    h2.cmdPrint('ip route del default')
    h2.cmdPrint('ip route add default via 10.0.0.7 dev h2-eth0')

    # Set up a routing rule on h2 to route packets via h7
    h3.cmdPrint('ip route del default')
    h3.cmdPrint('ip route add default via 10.0.0.7 dev h3-eth0')

    # Set up a routing rule on h2 to route packets via h3
    h4.cmdPrint('ip route del default')
    h4.cmdPrint('ip route add default via 10.0.0.7 dev h4-eth0')

    # Set up a routing rule on h5 to route packets via h3
    h5.cmdPrint('ip route del default')
    h5.cmdPrint('ip route add default via 10.0.0.7 dev h5-eth0')

    # h6 is a localhost.
    h6.cmdPrint('ip route del default')
    h6.cmdPrint('ip route add default via 10.0.0.7 dev h6-eth0')

    # The IDS runs on h8
    h8.cmdPrint('ip route del default')
    h8.cmdPrint('ip route add default via 10.0.0.7 dev h8-eth0')

    # The IDS runs on h8
    h9.cmdPrint('ip route del default')
    h9.cmdPrint('ip route add default via 10.0.0.7 dev h7-eth0')

    # Start dnsmasq (our dns server).
    h5.cmdPrint('/usr/sbin/dnsmasq --server  10.0.4.3 --pid-file=/tmp/dnsmasq.pid'  )

    #subprocess.Popen(cmd,shell=True,  stdin= subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, close_fds=False)
    #h2 is our peer same manufacturer host.
    h2.cmdPrint("python udpping.py --port 4000 --server &")
    # h6 is a localhost peer.
    h6.cmdPrint("python udpping.py --port 8002 --server &")
    
    # Start the IDS on node 8

    h8.cmdPrint("python packet-sniffer.py &")

    h7.cmdPrint('echo 0 > /proc/sys/net/ipv4/ip_forward')
    # Flush old rules.
    h7.cmdPrint('iptables -F')
    h7.cmdPrint('iptables -t nat -F')
    h7.cmdPrint('iptables -t mangle -F')
    h7.cmdPrint('iptables -X')
    # Set up h7 to be our router (it has two interfaces).
    h7.cmdPrint('echo 1 > /proc/sys/net/ipv4/ip_forward')
    # Set up iptables to forward as NAT
    h7.cmdPrint('iptables -t nat -A POSTROUTING -o h7-eth1 -s 10.0.0.0/24 -j MASQUERADE')
    # Set up our router routes.
    h7.cmdPrint('ip route add 203.0.113.13/32 dev h7-eth1')
    h7.cmdPrint('ifconfig h7-eth1 203.0.113.1 netmask 255.255.255.0')

    # Set up a router to reach the 'internet'
    h10.cmdPrint('ifconfig h10-eth0 203.0.113.13 netmask 255.255.255.0')
    # Start a web server there.
    h10.cmdPrint('python http-server.py -H 203.0.113.13&')

    # Ping the VNF host (past the lan)
  
    h1.cmdPrint('ping -c 10 203.0.113.13')


    print "*********** System ready *********"

    print "Install mud rules python postit.sh"

    print "register IDS python register-ids.sh"

    print "Exercise system python udpping.py --client --host 10.0.0.2 --port 4000"
    print "Exercise system python udpping.py --client --host 10.0.0.6 --port 8002"


    cli = CLI( net )
    h1.terminate()
    h2.terminate()
    h3.terminate()
    net.stop()

def startTestServer(host):
    """
    Start a test server to add to the allow access rules.
    """
    os.chdir("%s/mininet/testserver" % IOT_MUD_HOME)
    cmd = "/usr/bin/xterm -e \"/usr/bin/python testserver.py -H %s;bash\"" % host
    print cmd
    proc = subprocess.Popen(cmd,shell=True, stdin= subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, close_fds=True)  
    print "test server started"

if __name__ == '__main__':
    setLogLevel( 'info' )
    parser = argparse.ArgumentParser()
    # defaults to the address assigned to my VM
    parser.add_argument("-c",help="Controller host address",default=os.environ.get("CONTROLLER_ADDR"))
    parser.add_argument("-i",help="Host interface to route packets out (the second NATTed interface)",default="eth2")
    #parser.add_argument("-d",help="Public DNS address (check your resolv.conf)",default="192.168.11.1")
    
    parser.add_argument("-d",help="Public DNS address (check your resolv.conf)",default="10.0.4.3")
    parser.add_argument("-t",help="Host only adapter address for test server",default = "192.168.56.102")
    args = parser.parse_args()
    controller_addr = args.c
    dns_address = args.d
    host_addr = args.t
    interface = args.i

    # Clean up from the last invocation
    cmd = ['sudo','mn','-c']
    proc = subprocess.Popen(cmd,shell=False, stdin= subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    proc.wait()
    
    # Pkill dnsmasq. We will start one up later on h3
    if os.path.exists("/tmp/dnsmasq.pid"):
    	f = open('/tmp/dnsmasq.pid')
    	pid = int(f.readline())
    	try:
    	   os.kill(pid,signal.SIGTERM)
	except:
	   print "Failed to kill dnsmasq check if process is running"


    print("IMPORTANT : append 10.0.0.5 to resolv.conf")

    # start the test server.
    # startTestServer(host_addr)
    # setup our topology
    headers= {"Content-Type":"application/json"}
    for (configfile,suffix) in {("cpenodes.json","nist-cpe-nodes:cpe-collections"),("access-control-list.json","ietf-access-control-list:access-lists")
        ,("device-association.json","nist-mud-device-association:mapping"),("controllerclass-mapping.json","nist-mud-controllerclass-mapping:controllerclass-mapping"),
        ("ietfmud.json","ietf-mud:mud"),("npenodes.json","nist-network-topology:topology")} :
        data = json.load(open(configfile))
        print "configfile", configfile
        url = "http://" + controller_addr + ":8181/restconf/config/" + suffix
        print "url ", url
        r = requests.put(url, data=json.dumps(data), headers=headers , auth=('admin', 'admin'))
	    print "response ", r

    setupTopology(controller_addr,dns_address,interface)

