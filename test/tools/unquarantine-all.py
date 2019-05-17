
import requests
import json
import argparse

if __name__ == "__main__" :
    url =  "http://127.0.0.1:8181/restconf/operations/sdnmud:unquarantine-all"
    headers= {"Content-Type":"application/json"}
    r = requests.post(url,headers=headers , auth=('admin', 'admin'))
    print(r)

