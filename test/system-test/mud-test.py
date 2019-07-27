
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
import time
import requests
import json
from mininet.log import setLogLevel 
import unittest
import re
import os


#########################################################

global hosts



hosts = []

class TestAccess(unittest.TestCase) :

    def setUp(self):
        pass

    def tearDown(self):
        pass

    def runAndReturnOutput(self, host, command ):
        output = host.cmdPrint(command)
        retval = re.search('\[rc=(.+?)\]',output)
        pieces = retval.group(0).split('=')
        rc = pieces[1].split(']')[0]
        return rc


    def testAccessControl(self):
        print "wgetting from an allowed host -- this should succeed with MUD"
        h1 = hosts[0]
        result = h1.cmdPrint("wget http://www.nist.local:443 --timeout 30  --tries 2  -O foo.html --delete-after ")
        print "result = ",result
        # Check to see if the result was successful.
        self.assertTrue(re.search("100%",result) != None, "Expecting a successful get")

        print "wgetting from correct port on ontroller.nist.local host -- this should succeed."
        h2 = hosts[1]
        result = h2.cmdPrint("wget http://10.0.0.4:8080 --tries 2 --timeout 30 -O foo.html --delete-after")
        self.assertTrue(re.search("100%",result) is not None, "Expecting a successful get -- can access controller.nist.local on port 8080")


        print "udp pinging mycontroller host -- this should work"
        h1 = hosts[0]
        result = self.runAndReturnOutput(h1, "python ../unittest/util/udpping.py --port 4000 --host 10.0.0.4 --client --quiet")
        self.assertTrue(int(result) > 2, "expect successful ping")

        print "pinging a same manufacturer peer after configuration -- this should fail"
        result = h2.cmdPrint("ping -c 10  -q 10.0.0.1")
        self.assertTrue(re.search("100%",result) is not None, "Expecting a failed pings")

	print "Fetching from antd.local from sensor -- this should fail"
        result = h1.cmdPrint("wget http://www.antd.local:80 --tries 2 --timeout 20   -O foo.html --delete-after ")
        self.assertTrue(re.search("100%",result) is None, "Expecting a failed get")

        print  "icmp ping otherman -- this should fail"
        result = h1.cmdPrint("ping -c 10 -q 10.0.0.3")
        self.assertTrue(re.search("100%",result) is not None, "Expecting a failed icmp pings")

        print "udpping otherman on port 800"
        result = self.runAndReturnOutput(h1,"python ../unittest/util/udpping.py --port 800 --host 10.0.0.3 --client --quiet")
        self.assertTrue(int(result) >= 5, "expect successful ping")

        print "wget get from local net  from server on port 80 running on sensor - this should work"
        h5 = hosts[4]
        result = h5.cmdPrint("wget http://10.0.0.1:80 --tries 2 --timeout 20   -O foo.html --delete-after ")
        self.assertTrue(re.search("100%",result) is not None, "Expecting a successful get")
    
        print "wget get port 888 localnet from sensor "
        result = h5.cmdPrint("wget http://10.0.0.5:888 --tries 2 --timeout 20   -O foo.html --delete-after ")
        self.assertTrue(re.search("100%",result) is not None, "Expecting a successful get")

        print "wgetting from controller.nist.local host  on port 80 -- this should fail"
        h2=hosts[1]
        result = h2.cmdPrint("wget http://10.0.0.4 --tries 2 --timeout 30 -O foo.html --delete-after")
        self.assertTrue(re.search("100%",result) is None, "Expecting a failed get -- wrong port")

        print "wgetting from wrong direction - expect fail"
        h4 = hosts[3]
        result = h4.cmdPrint("wget http://10.0.0.1:8080 --tries 2 --timeout 30 -O foo.html --delete-after")
        self.assertTrue(re.search("100%",result) is None, "Expecting a failed get")



#########################################################



def cli():
    global net,c1,s1,s2,s3
    global h1,h2,h3,h4,h5,h6,h7,h8,h9,h10
    cli = CLI( net )
    h1.terminate()
    h2.terminate()
    h3.terminate()
    net.stop()


def setupTopology(controller_addr):
    global net,c1,s1,s2,s3
    global h1,h2,h3,h4,h5,h6,h7,h8,h9,h10
    "Create and run multiple link network"

    net = Mininet(controller=RemoteController)

    print "mininet created"

    c1 = net.addController('c1', ip=controller_addr,port=6653)


    # h1: IOT Device.
    # h2 : StatciDHCPD
    # h3 : router / NAT
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

    hosts.append(h1)
    hosts.append(h2)
    hosts.append(h3)
    hosts.append(h4)
    hosts.append(h5)
    hosts.append(h6)
    hosts.append(h7)
    hosts.append(h8)
    hosts.append(h9)
    hosts.append(h10)

    s2 = net.addSwitch('s2',dpid="2")
    s3 = net.addSwitch('s3',dpid="3")
    s1 = net.addSwitch('s1',dpid="1")

    s1.linkTo(h1)
    s1.linkTo(h2)
    s1.linkTo(h3)
    s1.linkTo(h4)
    s1.linkTo(h5)
    s1.linkTo(h6)
    s1.linkTo(h7)

    s2.linkTo(h8)
    s3.linkTo(h8)

    s3.linkTo(h9)
    s3.linkTo(h10)

    # S2 is the NPE switch.
    # Direct link between S1 and S2
    s1.linkTo(s2)


    h8.cmdPrint('echo 0 > /proc/sys/net/ipv4/ip_forward')
    # Flush old rules.
    h8.cmdPrint('iptables -F')
    h8.cmdPrint('iptables -t nat -F')
    h8.cmdPrint('iptables -t mangle -F')
    h8.cmdPrint('iptables -X')
    h8.cmdPrint('echo 1 > /proc/sys/net/ipv4/ip_forward')

    # Set up h3 to be our router (it has two interfaces).
    # Set up iptables to forward as NAT
    h8.cmdPrint('iptables -t nat -A POSTROUTING -o h8-eth1 -s 10.0.0.0/24 -j MASQUERADE')

    net.build()
    net.build()
    c1.start()
    s1.start([c1])
    s2.start([c1])
    s3.start([c1])

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
    h1.cmdPrint('ip route add default via 10.0.0.8 dev h1-eth0')

    # Set up a routing rule on h2 to route packets via h3
    h2.cmdPrint('ip route del default')
    h2.cmdPrint('ip route add default via 10.0.0.8 dev h2-eth0')

    # Set up a routing rule on h2 to route packets via h7
    h3.cmdPrint('ip route del default')
    h3.cmdPrint('ip route add default via 10.0.0.8 dev h3-eth0')

    # Set up a routing rule on h2 to route packets via h3
    h4.cmdPrint('ip route del default')
    h4.cmdPrint('ip route add default via 10.0.0.8 dev h4-eth0')

    # Set up a routing rule on h5 to route packets via h3
    h5.cmdPrint('ip route del default')
    h5.cmdPrint('ip route add default via 10.0.0.8 dev h5-eth0')

    # h6 is a localhost.
    h6.cmdPrint('ip route del default')
    h6.cmdPrint('ip route add default via 10.0.0.8 dev h6-eth0')

    # The IDS runs on h8
    h7.cmdPrint('ip route del default')
    h7.cmdPrint('ip route add default via 10.0.0.8 dev h7-eth0')

    # h9 is our fake host. It runs our "internet" web server.
    h9.cmdPrint('ifconfig h9-eth0 203.0.113.13 netmask 255.255.255.0')
    # Start a web server there.

    # h10 is our second fake host. It runs another internet web server that we cannot reach
    h10.cmdPrint('ifconfig h10-eth0 203.0.113.14 netmask 255.255.255.0')


    # Start dnsmasq (our dns server).
    h7.cmdPrint('/usr/sbin/dnsmasq --pid-file=/tmp/dnsmasq.pid --log-facility=/tmp/dnsmasq.log --dhcp-option=option:router,10.0.0.8'  )

    # Set up our router routes.
    h8.cmdPrint('ip route add 203.0.113.13/32 dev h8-eth1')
    h8.cmdPrint('ip route add 203.0.113.14/32 dev h8-eth1')
    h8.cmdPrint('ifconfig h8-eth1 203.0.113.1 netmask 255.255.255.0')
    
    setupServers()

    net.waitConnected()

    print "*********** System ready *********"

    return net

def setupServers():
    dns_host = h7
    dhcp_host = h7
    controller_mycontroller_sensor=h4
    controller_controller_nist_gov=h4
    laptop=h4
    sensor=h1
    sameman=h2
    otherman=h3
    localnet=h5
    controller_controller_nist_gov.cmdPrint("python -m SimpleHTTPServer  80&")
    controller_controller_nist_gov.cmdPrint("python -m SimpleHTTPServer  8080&")
    controller_mycontroller_sensor.cmdPrint("python ../unittest/util/udpping.py --port 4000 --server&") 
    otherman.cmdPrint("python ../unittest/util/udpping.py --port 800 --server&")
    h9.cmdPrint('python -m SimpleHTTPServer 443&')
    h10.cmdPrint('python -m SimpleHTTPServer 80&')
    #inbound connections allowed on port 80 http
    sensor.cmdPrint("python -m SimpleHTTPServer 80&")
    sensor.cmdPrint("python -m SimpleHTTPServer 8080&")
    sameman.cmdPrint("python -m SimpleHTTPServer 8888&")
    localnet.cmdPrint("python -m SimpleHTTPServer 888&")
    localnet.cmdPrint("python -m SimpleHTTPServer 80&")




def fixupResolvConf():
    # prepending 10.0.0.7 -- we want to go through our name resolution
    found = False
    with open("/etc/resolv.conf") as f :
	content = f.readlines() 
        found = False
        for line in content:
	    if line.find("10.0.0.7") != -1:
		found = True
		break

    print("10.0.0.7 not found in resolv.conf")
    if not found :
        original_data = None
        with open("/etc/resolv.conf") as f :
	    original_data = f.read()
	with open("/etc/resolv.conf","w") as f:
	     f.write("nameserver 10.0.0.7\n")
        with open("/etc/resolv.conf.save","w") as f :
             f.write(original_data)


    
def clean_mud_rules(controller_addr) :
    url =  "http://" + controller_addr + ":8181/restconf/operations/sdnmud:clear-mud-rules"
    headers= {"Content-Type":"application/json"}
    r = requests.post(url,headers=headers , auth=('admin', 'admin'))
    print r

if __name__ == '__main__':
    setLogLevel( 'info' )
    parser = argparse.ArgumentParser()
    # defaults to the address assigned to my VM
    parser.add_argument("-c",help="Controller host address",default=os.environ.get("CONTROLLER_ADDR"))

    parser.set_defaults(test=False)

    args = parser.parse_args()
    controller_addr = args.c
    test = args.test

    # Copy the dhclient file to the right place (or dhclient will not pick it up).
    cmd = [ 'cp', 'dhclient.conf.sensor', "/etc/dhcp/"]
    proc = subprocess.Popen(cmd,shell=False, stdin= subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    proc.wait()

    # Copy the dhclient file to the right place (or dhclient will not pick it up).
    cmd = [ 'cp', 'dhclient.conf.otherman', "/etc/dhcp/"]
    proc = subprocess.Popen(cmd,shell=False, stdin= subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    proc.wait()

    cmd = ['sudo','mn','-c']
    proc = subprocess.Popen(cmd,shell=False, stdin= subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    proc.wait()

    cmd = ['pkill', 'dnsmasq']
    proc = subprocess.Popen(cmd,shell=False, stdin= subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    proc.wait()


    clean_mud_rules(controller_addr)
    fixupResolvConf()
    net = setupTopology(controller_addr)

    headers= {"Content-Type":"application/json"}
    for (configfile,suffix) in { 
        ("sdnmud-config.json", "sdnmud:sdnmud-config"),
        ("controllerclass-mapping.json","nist-mud-controllerclass-mapping:controllerclass-mapping") }:
        data = json.load(open(configfile))
        print "configfile", configfile
        url = "http://" + controller_addr + ":8181/restconf/config/" + suffix
        print "url ", url
        r = requests.put(url, data=json.dumps(data), headers=headers , auth=('admin', 'admin'))
        print "response ", r
    
    h1.cmdPrint("nslookup www.nist.local")

    net.pingAll(1)
    h1.cmdPrint("nslookup www.nist.local")
    h1.cmdPrint("nslookup www.antd.local")

    h1.cmdPrint("ifconfig h1-eth0 0")
    h2.cmdPrint("ifconfig h2-eth0 0")
    h3.cmdPrint("ifconfig h3-eth0 0")
    h1.cmdPrint("dhclient -cf /etc/dhcp/dhclient.conf.sensor")
    h2.cmdPrint("dhclient -cf /etc/dhcp/dhclient.conf.sensor")
    h3.cmdPrint("dhclient -cf /etc/dhcp/dhclient.conf.otherman")

    if os.environ.get("UNITTEST") is not None and os.environ.get("UNITTEST") == '1' :
        time.sleep(5)
        unittest.main()
    else:
        cli()

