import requests
import json
import argparse
import os

if __name__=="__main__":
    if os.environ.get("CONTROLLER_ADDR") is None:
       print "Please set environment vaiable CONTROLLER_ADDR to the address of the opendaylight controller"

    controller_addr = os.environ.get("CONTROLLER_ADDR")

    headers= {"Content-Type":"application/json"}
    for (configfile,suffix) in {("cpenodes.json","nist-cpe-nodes:cpe-collections"),
        ("access-control-list.json","ietf-access-control-list:access-lists"),
        ("device-association.json","nist-mud-device-association:mapping"),
        ("controllerclass-mapping.json","nist-mud-controllerclass-mapping:controllerclass-mapping"),
        ("ietfmud.json","ietf-mud:mud") }:
        data = json.load(open(configfile))
        print "configfile", configfile
        url = "http://" + controller_addr + ":8181/restconf/config/" + suffix
        print "url ", url
        r = requests.put(url, data=json.dumps(data), headers=headers , auth=('admin', 'admin'))
        print "response ", r
