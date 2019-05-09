import requests
import json
import argparse

if __name__ == "__main__" :
    parser = argparse.ArgumentParser()
    parser.add_argument("-m", help="Metadata/mask in hex with a leading 0x. Cut and paste from dump-flows")
    args = parser.parse_args()
    srcmac = args.m
    argmap = {}
    innerMap = {}
    innerMap["mac-address"] = srcmac
    argmap["input"] = innerMap
    jsonStr = json.dumps(argmap, indent=4)
    print(jsonStr)
    url =  "http://127.0.0.1:8181/restconf/operations/sdnmud:get-src-mac-classification"
    headers= {"Content-Type":"application/json"}
    r = requests.post(url,headers=headers , auth=('admin', 'admin'), data=jsonStr)
    response = json.loads(r.content)
    print(json.dumps(response,indent=4))
