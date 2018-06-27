
import json

def padded_hex(given_int):
    given_len =12 

    hex_result = hex(given_int)[2:] # remove '0x' from beginning of str
    num_hex_chars = len(hex_result)
    extra_zeros = '0' * (given_len - num_hex_chars)
    return extra_zeros + hex_result

if __name__ == "__main__" :
    jsonDoc = {}
    cpeCollections = {}
    mac_addresses = []
    for i in range(1,101):
        addr = padded_hex(i)
        mac = ':'.join(s.encode('hex') for s in addr.decode('hex'))
        mac_addresses.append(mac)

    cpeCollections["device-id"] = mac_addresses    
    cpeCollections["mud-url"] = "https://toaster.nist.local/super1"
    jsonDoc["mapping"] = cpeCollections

    print json.dumps(jsonDoc, indent=8)
        

    


