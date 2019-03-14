### Startup System Configuration ###

Startup parameters for the system are defined in a file called sdnmud\_sdnmud-config.xml which is placed in the directory 

      karaf/target/assembly/etc/opendaylight/datastore/initial/config/

This defines static configuration parameters for the system.

[Here is the YANG model](../../sdnmud-aggregator/api/src/main/yang/sdnmud.yang)  with documented fields.

Here is an example of this file -- the default settings which are placed into the directory above :

        <?xml version="1.0" encoding="UTF-8"?>
        <sdnmud-config xmlns="urn:opendaylight:params:xml:ns:yang:sdnmud">
        <!-- The default location of the certificate store relative to $JAVA_HOME/jre
             If an absolute path is used here then $JAVA_HOME/jre is not prepended to the path.  -->
        <ca-certs> 
              lib/security/cacerts
        </ca-certs> 

        <!-- The key password for the certificates in the store -->
        <key-pass> 
               changeit
        </key-pass>

        <!-- Whether or not to trust self-signed certificates. Prototype only supports 
            self-signed certs.  -->
        <trust-self-signed-cert> 
                true
        </trust-self-signed-cert> 

        <!-- The cache timeout (s) for rules that associate manufactuerer with MAC address. 
         0 means infinite timeout. -->
        <mfg-id-rule-cache-timeout>
                60
        </mfg-id-rule-cache-timeout>

        <!-- Table start - the start location where we place the application 
             - which takes up three consequitive table entries and an entry in the drop-rule-table -->
        <table-start>
                0
        </table-start>

        <!-- The table where a drop rule is installed - unsuccessful MUD packets go here. This must be one table slot past where 
            baseapp installs its NORMAL flow rule. -->
        <drop-rule-table>
                4
        </drop-rule-table>

        <!-- A flag that indicates whether or not the ACL enforcement is "relaxed". 
            Relaxed ACLs eliminate pipeline disruptions but could result in temporary ACE 
            violations. -->
        <relaxed-acl>
                true
        </relaxed-acl>

        <!-- The port for mapping notifications - this is for IDS integration. Notification listeners
        are informed of MAC addresses that do not have MUD profiles via TCP on this port. -->
        <notification-port>
                30001
        </notification-port>

        </sdnmud-config>

If you want to change where in the pipeline the rules appear, you should
modify the location of table-start. The sdn mud application is composed
with a sample baseapp application which is simply a forwarder. You will
need to change the location of table-start if you want to merge this
application with another application.

### Specifying local networks and device controller classes ###

To support the "local-networks" class abstraction, the administrator needs to specify 
the local networks for each switch where the MUD rules will be installed. 
To support the controller class for MUD-enabled devices, the administrator needs to specify
the host addresses for the hosts on the local network that will serve as "device controllers".


This is specified using a REST URI:

      /restconf/config/nist-mud-controllerclass-mapping:controllerclass-mapping


(Note: we only specify the URI. 
The URL could be something like http://127.0.0.1:8181/restconf/config/nist-mud-controllerclass-mapping:controllerclass-mapping)


[The YANG model for the posted data is here](../../sdnmud-aggregator/api/src/main/yang/nist-mud-controllerclass-mapping.yang)

Detailed documentation for each of the fields is provided in the YANG model.


A sample controller class mapping file is shown here:

       {
       "controllerclass-mapping" :  {
            "switch-id" : "openflow:1",
            "controller" : [
            {
                    "uri" :  "urn:ietf:params:mud:dns",
                    "address-list" : [ "10.0.0.5" ]
            },
            {
                    "uri" :  "urn:ietf:params:mud:ntp",
                    "address-list" : [ "10.0.0.4" ]
            },
            {
                    "uri" :  "urn:ietf:params:mud:dhcp",
                    "address-list" : [ "10.0.0.6" ]
            },
            {
                    "uri": "https://toaster.nist.local/super1",
                    "address-list" : [ "10.0.0.7" ]
            }
            ],
            "local-networks-excluded-hosts": ["10.0.0.3",
            "local-networks": [ "10.0.0.0/24" ]
            }
        }


You must post a controller-class mapping for each managed switch using the REST URI above for MUD rules
to appear on those switches.


### Specifying which switches will be managed  ###

To install MUD rules, you must specify which switches are managed.
By default only the passthrough Base-app rules are installed when a switch connects.
The switches where MUD rules need to be installed are specified in a configuration file
that is posted to controller using a REST API for which the REST URI is:


      /restconf/config/nist-cpe-nodes:cpe-collections

[The YANG model](../../sdnmud-aggregator/api/src/main/yang/nist-cpe-nodes.yang) provides detailed documentation.

Here is a sample CPE collections file indicating that switches 1 and 2 will be managed:

     
   "cpe-collections" : {
               "cpe-switches" : [ "openflow:1", "openflow:2" ]
      }
    }

Please note that the switch will present its identifier as a hex string to the controller. 
These identifiers are in decimal. We'll leave the conversion from hex to decimal as an exercise
for the reader.


### Associating MAC addresses with MUD profiles ###

The MUD specification describes different methods for associating devices with MUD profiles. 
We support two such methods : 

* DHCP Options 66 support. In this method, the device emits its own MUD URL. The DHCP (UDP) 
request is sent to the Controller, which then fetches the MUD profile. No configuration is 
necessary for this to work. However, the Manufactuer web site must be up and running and the
device must support issuing a MUR URL as part of the DHCP Request.

* Associate MAC address with MUD profile by sending the controller a list of addresses
along with associated MUD urls. We have an API to support this. The configuration URI is


      /restconf/config/nist-mud-device-association:mapping

[The data to be posted with this URI is given by a YANG model](../../sdnmud-aggregator/api/src/main/yang/nist-mud-device-association.yang)

Here is a sample of the data that needs to be POSTed to the URI

      {
       "mapping":
        { 
            "device-id": [ "00:00:00:00:00:01",
                            "00:00:00:00:00:02",
                            "00:00:00:00:00:03"
                ],
            "mud-url": "https://toaster.nist.local/super1"
        }
     }   

The JSON file above indicates that the MAC addresses 01, 02 and 03 are associated with the https://toaster.nist.local/super1 MUD URL

At this point the server may fetch the MUD file from the manufacturer web site. For testing purposes, we also support POSTing the MUD
file to the server using another API. 




