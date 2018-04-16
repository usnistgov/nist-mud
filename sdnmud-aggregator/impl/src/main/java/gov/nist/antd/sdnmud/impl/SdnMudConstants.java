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

package gov.nist.antd.sdnmud.impl;

import java.math.BigInteger;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.mud.rev180301.Direction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowCookie;

public interface SdnMudConstants {
    // Well known ports.
    public static final int DNS_PORT = 53;
    public static final int DHCP_SERVER_PORT = 67;
    public static final int DHCP_CLIENT_PORT = 68;
    public static final int NTP_SERVER_PORT = 123;

    // Protocol ids.
    public static final short TCP_PROTOCOL = 6;
    public static final short UDP_PROTOCOL = 17;
    public static final short ICMP_PROTOCOL = 1;
    public static final int ETHERTYPE_IPV4 = 0x0800;

    // Well known classes.
    public static final String DNS_SERVER_URI = "urn:ietf:params:mud:dns";
    public static final String NTP_SERVER_URI = "urn:ietf:params:mud:ntp";

    // The mask for Manufacturer and model.
    public static final BigInteger SRC_MANUFACTURER_MASK = new BigInteger(
            "FFF0000000000000", 16);
    public static final int SRC_MANUFACTURER_SHIFT = "0000000000000".length()
            * 4;

    public static final BigInteger SRC_MODEL_MASK = new BigInteger(
            "000FFFF000000000", 16);
    public static final int SRC_MODEL_SHIFT = "000000000".length() * 4;

    public static final BigInteger DST_MANUFACTURER_MASK = new BigInteger(
            "0000000000000FFF", 16);
    public static final int DST_MANUFACTURER_SHIFT = 0;

    public static final BigInteger DST_MODEL_MASK = new BigInteger(
            "000000000ffff000", 16);
    public static final int DST_MODEL_SHIFT = "000".length() * 4;

    public static final BigInteger SRC_NETWORK_MASK = new BigInteger(
            "0000000F00000000", 16);
    public static final int SRC_NETWORK_FLAGS_SHIFT = "00000000".length() * 4;
    public static final BigInteger LOCAL_SRC_NETWORK_FLAG = new BigInteger(
            "0000000100000000", 16);

    public static final BigInteger DST_NETWORK_MASK = new BigInteger(
            "00000000F0000000", 16);
    public static final int DST_NETWORK_FLAGS_SHIFT = "0000000".length() * 4;
    public static final BigInteger LOCAL_DST_NETWORK_FLAG = new BigInteger(
            "0000000010000000", 16);

    // Cookie for unclassified flow rule.
    public static final String UNCLASSIFIED = "UNCLASSIFIED";
    // Flow URIs.
    public static final String NONE = "NONE";
    public static final String DROP = "drop";
    public static final String LOCAL = "local";
    public static final String REMOTE = "remote";

    // Well known cookies.
    public static final FlowCookie SEND_TO_CONTROLLER_FLOW_COOKIE = InstanceIdentifierUtils
            .createFlowCookie("send-to-controller-flow-cookie");

    public static final FlowCookie SRC_MANUFACTURER_STAMP_FLOW_COOKIE = InstanceIdentifierUtils
            .createFlowCookie("stamp-src-mac-manufacturer-model-flow-cookie");

    public static final FlowCookie DROP_FLOW_COOKIE = InstanceIdentifierUtils
            .createFlowCookie("DROP");

    public static final FlowCookie UNCLASSIFIED_FLOW_COOKIE = InstanceIdentifierUtils
            .createFlowCookie(UNCLASSIFIED);

    public static final FlowCookie SRC_LOCALNETWORK_MASK_FLOW_COOKIE = InstanceIdentifierUtils
            .createFlowCookie("src-local-network-flow-cookie");

    public static final FlowCookie DST_MANUFACTURER_MODEL_FLOW_COOKIE = InstanceIdentifierUtils
            .createFlowCookie("stamp-dst-mac-manufactuer-model-flow-cookie");

    public static final FlowCookie FROM_DEVICE_FLOW_COOKIE = InstanceIdentifierUtils
            .createFlowCookie(Direction.FromDevice.getName());
    public static final FlowCookie TO_DEVICE_FLOW_COOKIE = InstanceIdentifierUtils
            .createFlowCookie(Direction.ToDevice.getName());

    // Cache timeout for network and model stamping flow rules.
    public static final int ETHERTYPE_LLDP = 0x88cc;
    public static final BigInteger DEFAULT_METADATA_MASK = new BigInteger(
            "FFFFFFFFFFFFFFFF", 16);

}
