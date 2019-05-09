import requests
import json
import argparse

if __name__ == "__main__" :
    url =  "http://127.0.0.1:8181/restconf/operations/sdnmud:get-quarantine-macs"
    headers= {"Content-Type":"application/json"}
    r = requests.post(url,headers=headers , auth=('admin', 'admin'))
    response = json.loads(r.content)
    print(json.dumps(response,indent=4))
