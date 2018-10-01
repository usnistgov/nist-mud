import requests
import json
import argparse
import os
"""
Delete all the flows (for testing purposes).
"""

def clean_mud_rules(controller_addr) :
    url =  "http://" + controller_addr + ":8181/restconf/operations/sdnmud:clear-mud-rules"
    headers= {"Content-Type":"application/json"}
    r = requests.post(url,headers=headers , auth=('admin', 'admin'))
    print r

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("-c",help="Controller host address",default=os.environ.get("CONTROLLER_ADDR"))
    args = parser.parse_args()
    controller_addr = args.c
    clean_mud_rules(controller_addr)
