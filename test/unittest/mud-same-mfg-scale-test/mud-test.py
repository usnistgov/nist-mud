
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
import signal
import random
import time
import threading



#########################################################

global hosts

hosts = []




def cli():
    global net,c1,s1
    global hosts
    cli = CLI( net )
    for h in hosts:
        h.terminate()
    net.stop()

def padded_hex(given_int):
    given_len =12 

    hex_result = hex(given_int)[2:] # remove '0x' from beginning of str
    num_hex_chars = len(hex_result)
    extra_zeros = '0' * (given_len - num_hex_chars)
    return extra_zeros + hex_result

def setupTopology(controller_addr, nhosts):
    global net,c1,s1
    global hosts
    "Create and run multiple link network"

    net = Mininet(controller=RemoteController)

    print "mininet created"

    c1 = net.addController('c1', ip=controller_addr,port=6653)


    # h1: IOT Device.
    # h2 : StatciDHCPD
    # h3 : router / NAT
    # h4 : Non IOT device.
    for i in range(1,nhosts + 1) :
        hosts.append(net.addHost('h' + str(i)))


    s1 = net.addSwitch('s1',dpid="1")

    for i in range(0,len(hosts)):
        s1.linkTo(hosts[i])

    for i in range(0,len(hosts)):
        addr = padded_hex(i + 1)
        mac = ':'.join(s.encode('hex') for s in addr.decode('hex'))
        hosts[i].setMAC(mac,hosts[i].name + "-eth0")

    net.build()
    c1.start()
    s1.start([c1])
    net.start()
     

    # Clean up any traces of the previous invocation (for safety)




    print "*********** System ready *********"

    #net.stop()



def communicate(avgSleepTime=10, npings=10) :
    global hosts
    global result
    global doneFlag
    global cacheTimeout

    destaddr = []
    nhosts = len(hosts)
    for i in range(0,len(hosts)):
        addr = hosts[i].cmdPrint("hostname -I").strip()
        destaddr.append(addr)

    doneFlag = False

    while not doneFlag:
        source = random.randint(0, nhosts -1)
        dest = random.randint(0,nhosts -1)
        while dest == source :
            dest = random.randint(0,nhosts-1)
        print "src = " +  hosts[source].name + " dst = " + hosts[dest].name
        lambd = 1.0/avgSleepTime
        sleeptime = random.expovariate(lambd)
        time.sleep(sleeptime)
        res = hosts[source].cmdPrint("ping " + destaddr[dest]  + " -c " +str(npings) + " -q >> results/pingout." + str(cacheTimeout) + ".txt &")

    sys.exit()
    os.exit()
        

def sampleTableSize() :
    """
    Periodically sample the flow table and record its size.
    """
    global result
    global sampleCount
    global startTime
    global doneFlag
    global cacheTimeout


    times = {}
    times["seconds"] = int(time.time() - startTime)
           
    cmd = [ "sudo", "ovs-ofctl", "dump-flows", "s1", "-O", "openflow13" ]
    proc1 = subprocess.Popen(cmd,shell=False, stdin= subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    cmd = [ "wc", "-l" ]
    proc2 = subprocess.Popen(cmd,shell=False, stdin=proc1.stdout, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    stdout,stderr = proc2.communicate()
    times["table-size"] = int(stdout)

	 
    # The src manufacturer tag table.
    cmd = [ "sudo", "ovs-ofctl", "dump-flows", "s1", "-O", "openflow13" ]
    proc1 = subprocess.Popen(cmd,shell=False, stdin= subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    cmd = [ "grep", "-e", "table=3", "-e", "table=4" ]
    proc2 = subprocess.Popen(cmd,shell=False, stdin=proc1.stdout, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    cmd = [ "wc", "-l" ]
    proc3 = subprocess.Popen(cmd,shell=False, stdin=proc2.stdout, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    stdout,stderr = proc3.communicate()
    # Record the number of cached flow rules.
    times["cache-size"] = int(stdout) - 4

    if not "cache-sizes" in result:
        result["cache-sizes"] = []

    result["cache-sizes"].append(times)

    if sampleCount == 100:
        with  open("results/result." + str(cacheTimeout) + ".json","w") as res:
            resultStr = json.dumps(result, indent=8)
            res.write(resultStr)
            doneFlag = True
            sys.exit()
            os.exit()
    else:
    	sampleCount = sampleCount + 1
        threading.Timer(5,sampleTableSize).start()

if __name__ == '__main__':
    setLogLevel( 'info' )
    parser = argparse.ArgumentParser()
    # defaults to the address assigned to my VM
    parser.add_argument("-c",help="Controller host address",default=os.environ.get("CONTROLLER_ADDR"))
    parser.add_argument("-d",help="Public DNS address (check your resolv.conf)",default="10.0.4.3")

    parser.set_defaults(test=False)

    args = parser.parse_args()
    controller_addr = args.c
    test = args.test


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

    setupTopology(controller_addr,100)

    headers= {"Content-Type":"application/json"}
    for (configfile,suffix) in {("cpenodes.json","nist-cpe-nodes:cpe-collections"),
        ("access-control-list.json","ietf-access-control-list:acls"),
        ("device-association-toaster-100.json","nist-mud-device-association:mapping"),
        ("controllerclass-mapping.json","nist-mud-controllerclass-mapping:controllerclass-mapping"),
        ("sdnmud-config.json", "sdnmud:sdnmud-config"),
        ("ietfmud.json","ietf-mud:mud")} :
        with open(configfile) as f:
            data = json.load(f)
            print "configfile", configfile
            url = "http://" + controller_addr + ":8181/restconf/config/" + suffix
            print "url ", url
            r = requests.put(url, data=json.dumps(data), headers=headers , auth=('admin', 'admin'))
            print "response ", r

    if os.environ.get("UNITTEST") is not None and os.environ.get("UNITTEST") == '1' :
        time.sleep(2)
        with open("sdnmud-config.json") as f:
            config = json.load(f)
        print config
        global result
        global cacheTimeout
        global startTime
        global doneFlag 
        global sampleCount

        sampleCount = 0
        doneFlag = False
        result = {}
        cacheTimeout  = config['sdnmud-config']['mfg-id-rule-cache-timeout']
        # The src manufacturer tag table.
        cmd = [ "sudo", "ovs-ofctl", "dump-flows", "s1", "-O", "openflow13" ]
        proc1 = subprocess.Popen(cmd,shell=False, stdin= subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        cmd = [ "grep", "-e", "table=5" ]
        proc2 = subprocess.Popen(cmd,shell=False, stdin=proc1.stdout, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        cmd = [ "wc", "-l" ]
        proc3 = subprocess.Popen(cmd,shell=False, stdin=proc2.stdout, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        stdout,stderr = proc3.communicate()
        result["mud-rules-table-size"] = int(stdout)
        result["cache-timeout"] = cacheTimeout
        startTime = time.time()
        sampleTableSize()
        communicate(avgSleepTime = 5 , npings = 10)
    else:
        cli()

