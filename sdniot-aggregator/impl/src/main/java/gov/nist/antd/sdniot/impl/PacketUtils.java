/**
 * Copyright (c) 2014 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package gov.nist.antd.sdniot.impl;

import java.nio.ByteBuffer;
import java.util.Arrays;
import org.opendaylight.genius.mdsalutil.packet.Ethernet;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnectorKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflow.common.types.rev130731.EtherType;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import com.google.common.primitives.UnsignedInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public abstract class PacketUtils {

  /**
   * size of MAC address in octets (6*8 = 48 bits)
   */
  private static final int MAC_ADDRESS_SIZE = 6;

  /**
   * start position of destination MAC address in array
   */
  private static final int DST_MAC_START_POSITION = 0;

  /**
   * end position of destination MAC address in array
   */
  private static final int DST_MAC_END_POSITION = DST_MAC_START_POSITION + MAC_ADDRESS_SIZE;

  /**
   * start position of source MAC address in array
   */
  private static final int SRC_MAC_START_POSITION = 6;

  /**
   * end position of source MAC address in array
   */
  private static final int SRC_MAC_END_POSITION = SRC_MAC_START_POSITION + MAC_ADDRESS_SIZE;

  /**
   * start position of ethernet type in array
   */
  private static final int ETHER_TYPE_START_POSITION = 12;

  /**
   * end position of ethernet type in array
   */
  private static final int ETHER_TYPE_END_POSITION = 14;

  private static final Logger LOG = LoggerFactory.getLogger(PacketUtils.class);

  private PacketUtils() {
    // prohibite to instantiate this class
  }

  /**
   * @param payload
   * @return destination MAC address
   */
  public static byte[] extractDstMac(final byte[] payload) {
    return Arrays.copyOfRange(payload, DST_MAC_START_POSITION, DST_MAC_END_POSITION);
  }

  /**
   * @param payload
   * @return source MAC address
   */
  public static byte[] extractSrcMac(final byte[] payload) {
    return Arrays.copyOfRange(payload, SRC_MAC_START_POSITION, SRC_MAC_END_POSITION);
  }

  /**
   * @param payload
   * @return source MAC address
   */
  public static byte[] extractEtherType(final byte[] payload) {
    return Arrays.copyOfRange(payload, ETHER_TYPE_START_POSITION, ETHER_TYPE_END_POSITION);
  }

  /**
   * @param rawMac
   * @return {@link MacAddress} wrapping string value, baked upon binary MAC address
   */
  public static MacAddress rawMacToMac(final byte[] rawMac) {
    MacAddress mac = null;
    if (rawMac != null) {
      StringBuilder sb = new StringBuilder();
      for (byte octet : rawMac) {
        sb.append(String.format(":%02X", octet));
      }
      mac = new MacAddress(sb.substring(1));
    }
    return mac;
  }

  public static int bytesToEtherType(final byte[] bytes) {
    int etherType = (0x0000ffff & ByteBuffer.wrap(bytes).getShort());
    return etherType;
  }

  /**
   * get the src MacAddress
   */
  public static MacAddress extractSrcMacAddress(byte[] payload) {
    byte[] srcMac = extractSrcMac(payload);
    return rawMacToMac(srcMac);
  }

  /**
   * get the dst MacAddress
   */
  public static MacAddress extractDstMacAddress(byte[] payload) {
    byte[] dstMac = extractDstMac(payload);
    return rawMacToMac(dstMac);
  }


}
