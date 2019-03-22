
from mininet.node import OVSController
from mininet.cli import CLI
from mininet.log import setLogLevel
from mininet.net import Mininet
from mininet.node import RemoteController
from mininet.topo import Topo
from mininet.log import setLogLevel 

import pdb
import subprocess
import argparse
import os
import sys
import signal
from subprocess import call
import time
import requests
import json
import re
import os
import signal
import random
import time
import threading
import numpy as np



#########################################################

global _hosts
global _packet_classification_flow_table_sizes
global debug

_hosts = []
_loss = []
_packet_classification_flow_table_sizes = []
_l2switch_flow_table_size = []
debug = False




def cli():
    global net,c1,s1
    global _hosts
    cli = CLI( net )
    for h in _hosts:
        h.terminate()
    net.stop()

def padded_hex(given_int):
    given_len =12 

    hex_result = hex(given_int)[2:] # remove '0x' from beginning of str
    num_hex_chars = len(hex_result)
    extra_zeros = '0' * (given_len - num_hex_chars)
    return extra_zeros + hex_result

def setupTopology(controller_addr, n_hosts):
    global net,c1,s1
    global _hosts
    "Create and run multiple link network"

    net = Mininet(controller=RemoteController)

    print "mininet created"

    c1 = net.addController('c1', ip=controller_addr,port=6653)


    # h1: IOT Device.
    # h2 : StatciDHCPD
    # h3 : router / NAT
    # h4 : Non IOT device.
    for i in range(1,n_hosts + 1) :
        _hosts.append(net.addHost('h' + str(i)))


    s1 = net.addSwitch('s1',dpid="1")

    for i in range(0,len(_hosts)):
        s1.linkTo(_hosts[i])

    for i in range(0,len(_hosts)):
        addr = padded_hex(i + 1)
        mac = ':'.join(s.encode('hex') for s in addr.decode('hex'))
        _hosts[i].setMAC(mac,_hosts[i].name + "-eth0")

    net.build()
    c1.start()
    s1.start([c1])
    net.start()
     

    # Clean up any traces of the previous invocation (for safety)




    print "*********** System ready *********"

    #net.stop()



def communicate(avgSleepTime=10, npings=10) :
    global _hosts
    global _result
    global _doneFlag
    global _cacheTimeout
    global _packetsSent
    global _topoSize
    global _loss

    destaddr = []
    n_hosts = len(_hosts)
    for i in range(0,len(_hosts)):
        addr = _hosts[i].cmdPrint("hostname -I").strip()
        destaddr.append(addr)

    _doneFlag = False

    while not _doneFlag:
        source = random.randint(0, n_hosts -1)
        dest = random.randint(0,n_hosts -1)
        while dest == source :
            dest = random.randint(0,n_hosts-1)
        print "src = " +  _hosts[source].name + " dst = " + _hosts[dest].name
        lambd = 1.0/avgSleepTime
        sleeptime = random.expovariate(lambd)
        time.sleep(sleeptime)
        res = _hosts[source].cmdPrint("ping " + destaddr[dest]  + " -c " +str(npings) + " -q " )
        print res.split("received, ")[1]
        
 
        loss =  int(res.split("received, ")[1].split("%")[0])

        _packetsSent = _packetsSent + npings
        _loss.append(loss)

    sys.exit()
    os.exit()


def mode(data):
    lst =[]
    hgh=0
    for i in range(len(data)):
        lst.append(data.count(data[i]))
    m= max(lst)
    ml = [x for x in data if data.count(x)==m ] #to find most frequent values
    mode = []
    for x in ml: #to remove duplicates of mode
        if x not in mode:
           mode.append(x)
    return mode[0] if len(mode) > 0 else None
        

def sampleTableSize() :
    """
    Periodically sample the flow table and record its size.
    """
    global _result
    global _sampleCount
    global _startTime
    global _doneFlag
    global _cacheTimeout
    global _topoSize
    global _packet_classification_flow_table_sizes
    global _l2switch_flow_table_size
    global _packetsSent


    times = {}
    times["seconds"] = int(time.time() - _startTime)
           
    cmd = [ "sudo", "ovs-ofctl", "dump-flows", "s1", "-O", "openflow13" ]
    proc1 = subprocess.Popen(cmd,shell=False, stdin= subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    cmd = [ "wc", "-l" ]
    proc2 = subprocess.Popen(cmd,shell=False, stdin=proc1.stdout, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    stdout,stderr = proc2.communicate()
    times["all-flows"] = int(stdout)

     
    # The src manufacturer tag and the dest manufacturer table.
    cmd = [ "sudo", "ovs-ofctl", "dump-flows", "s1", "-O", "openflow13" ]
    proc1 = subprocess.Popen(cmd,shell=False, stdin= subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    cmd = [ "grep", "-e", "table=3", "-e", "table=4" ]
    proc2 = subprocess.Popen(cmd,shell=False, stdin=proc1.stdout, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    cmd = [ "wc", "-l" ]
    proc3 = subprocess.Popen(cmd,shell=False, stdin=proc2.stdout, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    stdout,stderr = proc3.communicate()
    print "mfg-flows = ",stdout
    times["manufacturer-tag-flows"] =   int(stdout) - 4 if int(stdout) - 4 >= 0  else 0

    _packet_classification_flow_table_sizes.append(times["manufacturer-tag-flows"])

    # The l2switch table.
    cmd = [ "sudo", "ovs-ofctl", "dump-flows", "s1", "-O", "openflow13" ]
    proc1 = subprocess.Popen(cmd,shell=False, stdin= subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    cmd = [ "grep", "-e", "table=9"]
    proc2 = subprocess.Popen(cmd,shell=False, stdin=proc1.stdout, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    cmd = [ "wc", "-l" ]
    proc3 = subprocess.Popen(cmd,shell=False, stdin=proc2.stdout, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    stdout,stderr = proc3.communicate()
    times["l2switch-flows"] =   int(stdout)
    _l2switch_flow_table_size.append(int(stdout))

    # Get the number of packets seen at the controller.
    url =  "http://" + controller_addr + ":8181/restconf/operations/sdnmud:get-packet-count"
    headers= {"Content-Type":"application/json"}
    r = requests.post(url,headers=headers , auth=('admin', 'admin'))
    result = r.json()
    times["total-packet-count"] = result["output"]["packet-count"]
    times["mud-packet-count"] = result["output"]["mud-packet-count"]
    print times

    if debug :
        if not "cache-sizes" in _result:
            _result["cache-sizes"] = []

        _result["cache-sizes"].append(times)
    else:
        print "iterationCounter = " , _sampleCount
        print str(times)
        print "*************************"

    # Give it 10 minutes for the cache to settle down.
    if _sampleCount >= 450:
        cmd = [ "sudo", "ovs-ofctl", "dump-flows", "s1", "-O", "openflow13" ]
        proc1 = subprocess.Popen(cmd,shell=False, stdin= subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        cmd = [ "grep", "-e", "table=5" ]
        proc2 = subprocess.Popen(cmd,shell=False, stdin=proc1.stdout, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        cmd = [ "wc", "-l" ]
        proc3 = subprocess.Popen(cmd,shell=False, stdin=proc2.stdout, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        stdout,stderr = proc3.communicate()
        _doneFlag = True
        _result["mud-rules-flow-table-size"] = int(stdout)
        _result["avg-packet-loss"] = np.mean(_loss)
        _result["median-packet-loss"] = np.median(_loss)
        _result["mode-packet-loss"] = mode(_loss)
        _result["max-packet-loss"] = np.max(_loss)
        _result["max-packet-classification-flow-table-size"] = np.max(_packet_classification_flow_table_sizes)
        _result["max-l2-switch-flow-table-size"] = np.max(_l2switch_flow_table_size)
        _result["avg-sleep-time-between-pings-seconds"] = 5
        _result["number-of-iot-devices"]  = _topoSize
        _result["pings-per-burst"] = 10
        _result["packets-sent"] = _packetsSent
        _result["controller_packets_per_packet_sent"] = float(result["output"]["mud-packet-count"]) / float(_packetsSent)

        if not os.path.exists('results/' + str(_topoSize)):
            os.mkdir("results/" + str(_topoSize))
    
        with  open("results/" + str(_topoSize) + "/result."  + str(_cacheTimeout) + ".json","w") as res:
            resultStr = json.dumps(_result, indent=8)
            res.write(resultStr)
            sys.exit()
            os.exit()
    else:
     	if _sampleCount == 350:
            # Clear the packet count at 300.
	    url =  "http://" + controller_addr + ":8181/restconf/operations/sdnmud:clear-packet-count"
            headers= {"Content-Type":"application/json"}
            r = requests.post(url,headers=headers , auth=('admin', 'admin'))
            print "Cleared packet count"
            _packetsSent = 0
        _sampleCount = _sampleCount + 1
        threading.Timer(5,sampleTableSize).start()

if __name__ == '__main__':
    setLogLevel( 'info' )
    global _topoSize
    global _packetsSent
    parser = argparse.ArgumentParser()
    # defaults to the address assigned to my VM
    parser.add_argument("-c",help="Controller host address",default=os.environ.get("CONTROLLER_ADDR"))
    parser.add_argument("-f",help="Config file",default="sdnmud-config.json")
    parser.add_argument("-s",help="topology size", type=int, default=100)

    parser.set_defaults(test=False)

    args = parser.parse_args()
    controller_addr = args.c
    cfgfile = args.f
    _topoSize = args.s
    
    print "cfgfile ", cfgfile
    
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

    if not os.path.isdir("results/" + str(_topoSize)):
       os.makedirs("results/" + str(_topoSize))



    url =  "http://" + controller_addr + ":8181/restconf/operations/sdnmud:clear-packet-count"
    headers= {"Content-Type":"application/json"}
    r = requests.post(url,headers=headers , auth=('admin', 'admin'))
    url =  "http://" + controller_addr + ":8181/restconf/operations/sdnmud:clear-cache"
    headers= {"Content-Type":"application/json"}
    r = requests.post(url,headers=headers , auth=('admin', 'admin'))
    for (configfile,suffix) in {
        (cfgfile, "sdnmud:sdnmud-config"),
        ("device-association-toaster-100.json","nist-mud-device-association:mapping"),
        ("controllerclass-mapping.json","nist-mud-controllerclass-mapping:controllerclass-mapping"),
	} :
        with open(configfile) as f:
            data = json.load(f)
            print "configfile", configfile
            url = "http://" + controller_addr + ":8181/restconf/config/" + suffix
            print "url ", url
            r = requests.put(url, data=json.dumps(data), headers=headers , auth=('admin', 'admin'))
            print "response ", r

    setupTopology(controller_addr,_topoSize)

    if os.environ.get("UNITTEST") is not None and os.environ.get("UNITTEST") == '1' :
        time.sleep(2)
        with open(cfgfile) as f:
            config = json.load(f)
        print config
        global _result
        global _cacheTimeout
        global _startTime
        global _doneFlag 
        global _sampleCount
        global _packetsSent

        _packetsSent = 0
        _sampleCount = 0
        _doneFlag = False
        _result = {}
        _cacheTimeout  = config['sdnmud-config']['mfg-id-rule-cache-timeout']
        if os.path.exists("results/" + str(_topoSize) + "/pingout." + str(_cacheTimeout) + ".txt"):
            os.remove("results/" + str(_topoSize) + "/pingout." + str(_cacheTimeout) + ".txt")
        # The src manufacturer tag table.
        _result["cache-timeout"] = _cacheTimeout
        _result["relaxed-acl"] = config['sdnmud-config']['relaxed-acl']
        _startTime = time.time()
        _packetsSent = 0
        sampleTableSize()
        communicate(avgSleepTime = 5 , npings = 10)
    else:
        cli()

