/*
 *This file includes code developed by employees of the National Institute of
 * Standards and Technology (NIST)
 *
 * This software was developed by employees of the National Institute of
 * Standards and Technology (NIST), and others. This software has been
 * contributed to the  domain. Pursuant to title 15 Untied States
 * Code Section 105, works of NIST employees are not subject to copyright
 * protection in the United States and are considered to be in the
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

package gov.nist.antd.sdnmud.impl;

import java.math.BigInteger;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.rev190128.Direction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowCookie;

interface SdnMudConstants {
	// Well known ports.
	int DNS_PORT = 53;
	int DHCP_SERVER_PORT = 67;
	int DHCP_CLIENT_PORT = 68;
	int NTP_SERVER_PORT = 123;

	// Protocol ids.
	short TCP_PROTOCOL = 6;
	short UDP_PROTOCOL = 17;
	int ETHERTYPE_IPV4 = 0x0800;
	int ETHERTYPE_CUSTOMER_VLAN = 0x08100;

	// Well known classes.
	String DNS_SERVER_URI = "urn:ietf:params:mud:dns";
	String NTP_SERVER_URI = "urn:ietf:params:mud:ntp";


	// Split the metadata in two. Top half for src.
	static final int SRC_MANUFACTURER_SHIFT = 32;
	static final BigInteger SRC_MANUFACTURER_MASK = BigInteger.valueOf(0xFFF).shiftLeft(SRC_MANUFACTURER_SHIFT); //12 bits for src manufacturer
	
	static final int SRC_MODEL_SHIFT = SRC_MANUFACTURER_MASK.bitLength();
	static final BigInteger SRC_MODEL_MASK = BigInteger.valueOf(0xFFF).shiftLeft(SRC_MODEL_SHIFT);
	
	static final int SRC_NETWORK_FLAGS_SHIFT = SRC_MODEL_MASK.bitLength();
	static final BigInteger LOCAL_SRC_NETWORK_FLAG = BigInteger.valueOf(1L).shiftLeft(SRC_NETWORK_FLAGS_SHIFT);
	static final BigInteger SRC_NETWORK_MASK = LOCAL_SRC_NETWORK_FLAG;
	
	static final int SRC_QUARANTENE_MASK_SHIFT = SRC_NETWORK_MASK.bitLength();
	static final BigInteger SRC_QUARANTENE_FLAG = BigInteger.valueOf(1L).shiftLeft(SRC_QUARANTENE_MASK_SHIFT);
	// ONE bit for src quarantine mask.
	static final BigInteger SRC_QUARANTENE_MASK = SRC_QUARANTENE_FLAG;
	
	static final int SRC_MAC_BLOCKED_MASK_SHIFT = SRC_QUARANTENE_MASK.bitLength();
	static final BigInteger SRC_MAC_BLOCKED_FLAG = BigInteger.valueOf(1L).shiftLeft(SRC_MAC_BLOCKED_MASK_SHIFT);
	static final BigInteger SRC_MAC_BLOCKED_MASK = SRC_MAC_BLOCKED_FLAG;


	static final BigInteger DST_MANUFACTURER_MASK = BigInteger.valueOf(0xFFF); // 12 bits for dst manufacturer.
	static final int DST_MANUFACTURER_SHIFT = 0;
	
	static final int DST_MODEL_SHIFT = DST_MANUFACTURER_MASK.bitLength();
	static final BigInteger DST_MODEL_MASK = BigInteger.valueOf(0xFFF).shiftLeft(DST_MODEL_SHIFT);
	
	static final int DST_NETWORK_FLAGS_SHIFT = DST_MODEL_MASK.bitLength();
	static final BigInteger LOCAL_DST_NETWORK_FLAG = BigInteger.valueOf(1L).shiftLeft(DST_NETWORK_FLAGS_SHIFT);
	static final BigInteger DST_NETWORK_MASK = LOCAL_DST_NETWORK_FLAG;
	
	static final int DST_QUARANTENE_FLAGS_SHIFT = DST_NETWORK_MASK.bitLength();
	static final BigInteger DST_QUARANTENE_FLAG = BigInteger.valueOf(1L).shiftLeft(DST_QUARANTENE_FLAGS_SHIFT);
	static final BigInteger DST_QURANTENE_MASK = DST_QUARANTENE_FLAG;
	
	static final int DST_MAC_BLOCKED_MASK_SHIFT = DST_QURANTENE_MASK.bitLength();
	static final BigInteger DST_MAC_BLOCKED_FLAG = BigInteger.valueOf(1L).shiftLeft(DST_MAC_BLOCKED_MASK_SHIFT);
	static final BigInteger DST_MAC_BLOCKED_MASK = DST_MAC_BLOCKED_FLAG;

	// Classification for UNKNOWN packet ( initial value before lookup )
	static final String UNKNOWN = "UNKNOWN";

	// Cookie for unclassified flow rule.
	static final String UNCLASSIFIED = "UNCLASSIFIED";
	// Flow URIs.
	static final String NONE = "NONE";
	static final String DROP = "drop";
	
	// Well known cookies.
	static final FlowCookie SEND_TO_CONTROLLER_FLOW_COOKIE = IdUtils.createFlowCookie("send-to-controller-flow-cookie");

	static final FlowCookie SEND_TCP_PACKET_TO_CONTROLLER_FLOW_COOKIE = IdUtils
			.createFlowCookie("send-tcp-packet-to-controller-flow-cookie");

	static final FlowCookie BYPASS_DHCP_FLOW_COOKIE = IdUtils.createFlowCookie("bypass-dhcp-flow-cookie");

	static final FlowCookie SRC_MANUFACTURER_STAMP_FLOW_COOKIE = IdUtils
			.createFlowCookie("stamp-src-mac-manufacturer-model-flow-cookie");

	static final FlowCookie DROP_FLOW_COOKIE = IdUtils.createFlowCookie("DROP");

	static final FlowCookie GOTO_NEXT_FLOW_COOKIE = IdUtils.createFlowCookie("GOTO_NEXT_FLOW_COOKIE");

	static final FlowCookie UNCLASSIFIED_FLOW_COOKIE = IdUtils.createFlowCookie(UNCLASSIFIED);

	static final FlowCookie SRC_LOCALNETWORK_MASK_FLOW_COOKIE = IdUtils.createFlowCookie("src-local-network-flow-cookie");

	static final FlowCookie DST_MANUFACTURER_MODEL_FLOW_COOKIE = IdUtils
			.createFlowCookie("stamp-dst-mac-manufactuer-model-flow-cookie");

	static final FlowCookie FROM_DEVICE_FLOW_COOKIE = IdUtils.createFlowCookie(Direction.FromDevice.getName());
	static final FlowCookie TO_DEVICE_FLOW_COOKIE = IdUtils.createFlowCookie(Direction.ToDevice.getName());

	static final FlowCookie DH_REQUEST_FLOW_COOKIE = IdUtils.createFlowCookie("dhcp-request-flow-cookie");
	static final FlowCookie DNS_REQUEST_FLOW_COOKIE = IdUtils.createFlowCookie("dns-request-flow-cookie");
	static final FlowCookie DNS_RESPONSE_FLOW_COOKIE = IdUtils.createFlowCookie("dns-response-flow-cookie");
	static final FlowCookie DEFAULT_MUD_FLOW_COOKIE = IdUtils.createFlowCookie("default-mud-flow-cookie");
	static final FlowCookie TCP_SYN_MATCH_CHECK_COOKIE = IdUtils.createFlowCookie("tcp-syn-match-check");
	static final FlowCookie BLOCK_SRC_MAC_FLOW_COOKIE = IdUtils.createFlowCookie("blocked-src-mac-flow-cookie");
	static final FlowCookie BLOCK_DST_MAC_FLOW_COOKIE = IdUtils.createFlowCookie("blocked-dst-mac-flow-cookie");

	// Cache timeout for network and model stamping flow rules.
	static final int ETHERTYPE_LLDP = 0x88cc;
	static final BigInteger DEFAULT_METADATA_MASK = new BigInteger("FFFFFFFFFFFFFFFF", 16);

	static final String DEST_MAC_MATCH_SET_METADATA_AND_GOTO_NEXT_FLOWID_PREFIX = "/sdnmud/destMacMatchSetMetadataAndGoToNextTable/";

	static final String SRC_MAC_MATCH_SET_METADATA_AND_GOTO_NEXT_FLOWID_PREFIX = "/sdnmud/srcMacMatchSetMetadataAndGoToNextTable/";

	//boolean IMPLEMENT_MODEL_ACLS = false;
	// TODO -- set this in the config file.
	static final int DROP_RULE_TIMEOUT = 120;

	// Flow table priorities.
	static final Integer BASE_PRIORITY = 30;
	static final Integer SEND_PACKET_TO_CONTROLLER_PRIORITY = 0;

	// Flow entry for dropping flows on a match.
	static final Integer MAX_PRIORITY = BASE_PRIORITY + 25;
	static final Integer MATCHED_GOTO_ON_QUARANTENE_PRIORITY = BASE_PRIORITY + 20;
	static final Integer MATCHED_DROP_ON_QUARANTINE_PRIORITY = BASE_PRIORITY + 15;
	static final Integer MATCHED_GOTO_FLOW_PRIORITY = BASE_PRIORITY + 10;
	static final Integer MATCHED_DROP_PACKET_FLOW_PRIORITY = BASE_PRIORITY + 5;
	static final Integer UNCONDITIONAL_GOTO_PRIORITY = BASE_PRIORITY;
	static final Integer UNCONDITIONAL_DROP_PRIORITY = BASE_PRIORITY;

}
