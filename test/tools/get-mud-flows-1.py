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

# Dynamically pull logs when you are unaware of the MUD URL.

if __name__ == "__main__" :
    url =  "http://127.0.0.1:8181/restconf/operations/sdnmud:get-mud-urls"
    headers= {"Content-Type":"application/json"}
    argmap = {}
    innerMap = {}
    #innerMap["mud-uri"] = ["https://inside.nist.gov/foobar"]
    argmap["input"] = innerMap
    jsonStr = json.dumps(argmap, indent=4)
    print(jsonStr)
    while True:
        r = requests.post(url,headers=headers , auth=('admin', 'admin'), data=jsonStr)
        print("r = " + str(r.content))
        if r.content ==  b'{"output":{}}':
            time.sleep(10)
            continue
        else:
            print("got the mud file - can pull a log")
            break

    time.sleep(10)
    # OK if we got so far, we can get the log
    url = "http://127.0.0.1:8181/restconf/operations/sdnmud:get-mud-urls"
    print("jsonStr " + jsonStr)
    #r = requests.post(url,headers=headers , auth=('admin', 'admin'),data=jsonStr)
    print("Pulling the logs.")
    while True:
        try:
            jsonStr = json.dumps(argmap, indent=4)
            r = requests.post(url,headers=headers , auth=('admin', 'admin'),data=jsonStr)
            js = json.loads(r.content)
            print("*************************************************")
            print(json.dumps(js,indent=4))
            # TODO -- pull all the reports in. For now just one.
            if "report" in js["output"] :
                innerMap["mud-url"] = [js["output"]["report"]["mud-url"]]
                time.sleep(10)
            else:
                time.sleep(10)
        except:
            print(r.content)
        
    print(js["output"]["report"]["mud-report"]["drop-counts"][0])
