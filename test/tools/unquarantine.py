import requests
import json
import argparse

if __name__ == "__main__" :
    parser = argparse.ArgumentParser()
    parser.add_argument("-m", help="Mac address to unquarantine")
    args = parser.parse_args()
    mac_address = args.m
    innerMap = {}
    argmap = {}
    innerMap["device-mac-address"] = mac_address
    argmap["input"] = innerMap
    jsonStr = json.dumps(argmap, indent=4)
    print(jsonStr)
    url =  "http://127.0.0.1:8181/restconf/operations/sdnmud:unquarantine"
    headers= {"Content-Type":"application/json"}
    r = requests.post(url,headers=headers , auth=('admin', 'admin'), data=jsonStr)
    response = json.loads(r.content)
    print(json.dumps(response,indent=4))

