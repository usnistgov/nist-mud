import requests
import json
import argparse
import os

if __name__=="__main__":

    controller_addr = "127.0.0.1"

    headers= {"Content-Type":"application/json"}
    for (configfile,suffix) in {
        ("sdnmud-config.json", "sdnmud:sdnmud-config"),
        ("controllerclass-mapping.json","nist-mud-controllerclass-mapping:controllerclass-mapping")
        }:
        data = json.load(open(configfile))
        print ("configfile " + configfile)
        url = "http://" + controller_addr + ":8181/restconf/config/" + suffix
        print ("url " + url)
        r = requests.put(url, data=json.dumps(data), headers=headers , auth=('admin', 'admin'))
        print ("response " +  str(r))
