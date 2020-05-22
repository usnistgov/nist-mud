import requests
import json
import argparse
import os
from os import sys
import string
import time
import ast

def is_part(some_string, target):
    return target in some_string

if __name__ == "__main__" :
    parser = argparse.ArgumentParser()
    parser.add_argument("-m", help="metadata mask  ")
    args = parser.parse_args()
    srcmac = args.m
    if srcmac == None :
       print("Please provide metadata mask.")
       sys.exit()

    argmap = {}
    innerMap = {}
    innerMap["mud-url"] = srcmac
    argmap["input"] = innerMap
    jsonStr = json.dumps(argmap, indent=4)
    print(jsonStr)
    url =  "http://127.0.0.1:8181/restconf/operations/sdnmud:add-controller-wait-input"
    headers= {"Content-Type":"application/json"}
    while True:
        r = requests.post(url,headers=headers , auth=('admin', 'admin'), data=jsonStr)
        if r.content !=  b'{"output":{}}':
            time.sleep(10)
            continue
        else:
            print("got the mud file - can pull a log")
            break

    time.sleep(10)
    # OK if we got so far, we can get the log
    url = "http://127.0.0.1:8181/restconf/operations/sdnmud:get-mud-reports"
    r = requests.post(url,headers=headers , auth=('admin', 'admin'),data=jsonStr)
    while True:
        r = requests.post(url,headers=headers , auth=('admin', 'admin'), data=jsonStr)
        js = json.loads(r.content)
        print("*************************************************")
        print(json.dumps(js,indent=4))
        time.sleep(10)
        

