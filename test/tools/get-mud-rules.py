import requests
import json
import argparse

if __name__ == "__main__" :
    parser = argparse.ArgumentParser()
    parser.add_argument("--mud-url", help="Mud URL", default=None)
    parser.add_argument("--switch-id", help="Switch ID", default=None)
    args = parser.parse_args()
    mud_url = args.mud_url
    switch_id = args.switch_id

    argmap = {}
    innerMap = {}
    innerMap["mud-url"] = mud_url
    innerMap["switch-id"] = switch_id
    argmap["input"] = innerMap
    url =  "http://127.0.0.1:8181/restconf/operations/sdnmud:get-flow-rules"
    headers= {"Content-Type":"application/json"}
    jsonStr = json.dumps(argmap, indent=4)
    print(jsonStr)
    r = requests.post(url,headers=headers , auth=('admin', 'admin'), data=jsonStr)
    response = json.loads(r.content)
    
    print(json.dumps(response,indent=4))
