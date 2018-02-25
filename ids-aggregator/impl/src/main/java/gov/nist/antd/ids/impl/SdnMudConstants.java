/*
 *This file includes code developed by employees of the National Institute of
 * Standards and Technology (NIST)
 *
 * This software was developed by employees of the National Institute of
 * Standards and Technology (NIST), and others. This software has been
 * contributed to the public domain. Pursuant to title 15 Untied States
 * Code Section 105, works of NIST employees are not subject to copyright
 * protection in the United States and are considered to be in the public
 * domain. As a result, a formal license is not needed to use this software.
 *
 * This software is provided "AS IS." NIST MAKES NO WARRANTY OF ANY KIND,
 * EXPRESS, IMPLIED OR STATUTORY, INCLUDING, WITHOUT LIMITATION, THE
 * IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE,
 * NON-INFRINGEMENT AND DATA ACCURACY. NIST does not warrant or make any
 * representations regarding the use of the software or the results thereof,
 * including but not limited to the correctness, accuracy, reliability or
 * usefulness of this software.
 */
/**
 * Collection of constants used in the implementation.
 * 
 */

package gov.nist.antd.ids.impl;

import java.math.BigInteger;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowCookie;

public interface SdnMudConstants {
  public static final int DNS_PORT = 53;
  public static final int DHCP_SERVER_PORT = 67;
  public static final int DHCP_CLIENT_PORT = 68;
  public static final int NTP_SERVER_PORT = 123;

  public static final short TCP_PROTOCOL = 6;
  public static final short UDP_PROTOCOL = 17;
  public static final short ICMP_PROTOCOL = 1;
  public static final int ETHERTYPE_IPV4 = 0x0800;


  // Table where SDN MUD rules are stored.
  
  public static final Short SRC_DEVICE_MANUFACTURER_STAMP_TABLE = 0;
  public static final Short DST_DEVICE_MANUFACTURER_STAMP_TABLE = 1;
  public static final Short SDNMUD_RULES_TABLE = 2;
  public static final Short DROP_TABLE = (short) (SDNMUD_RULES_TABLE + 1);
  public static final Short PASS_THRU_TABLE = (short) (SDNMUD_RULES_TABLE + 2);
  public static final Short STRIP_MPLS_RULE_TABLE = (short) (SDNMUD_RULES_TABLE + 3);
  public static final Short L2SWITCH_TABLE = (short) (SDNMUD_RULES_TABLE + 4);
  public static final short FIRST_TABLE = 0;
  public static final Short  MAX_TID =  L2SWITCH_TABLE;


  // Flow table priorities.
  public static final Integer BASE_PRIORITY = 30;
  public static final Integer SEND_PACKET_TO_CONTROLLER_PRIORITY  = 0;
  
  // Flow entry for dropping flows on a match.
  public static final Integer MAX_PRIORITY = BASE_PRIORITY + 20;
  public static final Integer MATCHED_DROP_PACKET_FLOW_PRIORITY_HIGH = BASE_PRIORITY + 15;
  public static final Integer MATCHED_GOTO_FLOW_PRIORITY = BASE_PRIORITY + 10;
  public static final Integer MATCHED_DROP_PACKET_FLOW_PRIORITY = BASE_PRIORITY + 5;
  public static final Integer UNCONDITIONAL_GOTO_PRIORITY = BASE_PRIORITY;
  public static final Integer UNCONDITIONAL_DROP_PRIORITY = BASE_PRIORITY;
  public static final String DNS_SERVER_URI = "urn:ietf:params:mud:dns";
  public static final String NTP_SERVER_URI = "urn:ietf:params:mud:ntp";


  public static final String LOCAL = "local";
  public static final String REMOTE = "remote";
  public static final String NONE = "NONE";
  public static final String MUD_FLOW_MISS = "drop";

  // The "well known" IDS registration message.

  public static final String IDS_REGISTRATION_ADDRESS = "255.255.255.255";
  public static final int IDS_REGISTRATION_PORT = 1000;
  
  // The mask for Manufacturer and model.
  public static final BigInteger SRC_MANUFACTURER_MASK = new BigInteger("FFF0000000000000",16);
  public static final int SRC_MANUFACTURER_SHIFT =                         "0000000000000".length()*4;

  public static final BigInteger SRC_MODEL_MASK       =  new BigInteger("000FFFF000000000",16);
  public static final int SRC_MODEL_SHIFT =                                    "000000000".length()*4;

  public static final BigInteger DST_MANUFACTURER_MASK = new BigInteger("0000000000000FFF",16);
  public static final int DST_MANUFACTURER_SHIFT =                                   0;
  
  public static final BigInteger DST_MODEL_MASK =        new BigInteger("000000000ffff000",16);
  public static final int DST_MODEL_SHIFT       =                                    "000".length()*4;
  
  public static final BigInteger SRC_NETWORK_MASK  =     new BigInteger("0000000F00000000",16);
  public static final int SRC_NETWORK_FLAGS_SHIFT =                             "00000000".length()*4;
  public static final BigInteger LOCAL_SRC_NETWORK_FLAG= new BigInteger("0000000100000000",16);

  
  public static final BigInteger DST_NETWORK_MASK =      new BigInteger("00000000F0000000",16);
  public static final int DST_NETWORK_FLAGS_SHIFT =                               "0000000".length()*4;
  public static final BigInteger LOCAL_DST_NETWORK_FLAG= new BigInteger("0000000010000000",16);

		  
  public static final int DEFAULT_IDS_IDLE_TIMEOUT = 30;

  // Metadata for IDS registration.
  public static final BigInteger IDS_REGISTRATION_METADATA = BigInteger.valueOf(0xdeadbeefL);
  public static final FlowCookie IDS_REGISTRATION_FLOW_COOKIE =
      InstanceIdentifierUtils.createFlowCookie("ids-registration-flow-cookie");
  public static final FlowCookie SEND_TO_CONTROLLER_FLOW_COOKIE = 
      InstanceIdentifierUtils.createFlowCookie("send-to-controller-flow-cookie");
  
  // 0 means no caching.
  public static final Integer CACHE_TIMEOUT = 120;
}
