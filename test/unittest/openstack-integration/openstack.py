import requests
import json

loginStr = """{ "auth": {

    "identity": {

      "methods": ["password"],

      "password": {

        "user": {

          "name": "admin",

          "domain": { "id": "default" },

          "password": "projsdn"

        }

      }

    },

    "scope": {

      "project": {

        "name": "demo",

        "domain": { "id": "default" }

      }

    }

  }

}"""

if __name__ == "__main__":

    login = json.loads(loginStr)
    print "Login Str = " , json.dumps(login,indent=8)
    headers= {"Content-Type":"application/json"}
    openstack_addr = "10.0.42.10"
    url =  "http://" + openstack_addr + "/identity/v3/auth/tokens"
    r = requests.post(url,headers=headers , data = json.dumps(login))
    print r.headers["X-Subject-Token"]




