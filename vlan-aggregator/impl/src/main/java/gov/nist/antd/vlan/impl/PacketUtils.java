/*
 * Includes code that falls under the following repository
 * 
 * https://github.com/opendaylight/sfc 
 *
 * Copyright (c) 2014 Cisco Systems, Inc. and others. All rights reserved.
 * Copyright (c) 2015, 2017 Ericsson, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *  
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

package gov.nist.antd.vlan.impl;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
abstract class PacketUtils {

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

	private static final int PACKET_OFFSET_IP = 14;
	private static final int PACKET_OFFSET_IP_SRC = PACKET_OFFSET_IP + 12;
	private static final int PACKET_OFFSET_IP_DST = PACKET_OFFSET_IP + 16;


	private static final Logger LOG = LoggerFactory.getLogger(PacketUtils.class);

	private PacketUtils() {
		// prohibite to instantiate this class
	}

	/**
	 * @param payload
	 * @return destination MAC address
	 */
	static byte[] extractDstMac(final byte[] payload) {
		return Arrays.copyOfRange(payload, DST_MAC_START_POSITION, DST_MAC_END_POSITION);
	}

	/**
	 * @param payload
	 * @return source MAC address
	 */
	static byte[] extractSrcMac(final byte[] payload) {
		return Arrays.copyOfRange(payload, SRC_MAC_START_POSITION, SRC_MAC_END_POSITION);
	}

	/**
	 * @param payload
	 * @return the ethertype.
	 */
	static byte[] extractEtherType(final byte[] payload) {
		return Arrays.copyOfRange(payload, ETHER_TYPE_START_POSITION, ETHER_TYPE_END_POSITION);
	}

	/**
	 * @param rawMac
	 * @return {@link MacAddress} wrapping string value, baked upon binary MAC
	 *         address
	 */
	static MacAddress rawMacToMac(final byte[] rawMac) {
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

	static int bytesToEtherType(final byte[] bytes) {
		int etherType = (0x0000ffff & ByteBuffer.wrap(bytes).getShort());
		return etherType;
	}

	
	static MacAddress extractSrcMacAddress(byte[] payload) {
		byte[] srcMac = extractSrcMac(payload);
		return rawMacToMac(srcMac);
	}


	static MacAddress extractDstMacAddress(byte[] payload) {
		byte[] dstMac = extractDstMac(payload);
		return rawMacToMac(dstMac);
	}

	public static String extractDstIpStr(final byte[] rawPacket) {
		final byte[] ipDstBytes = Arrays.copyOfRange(rawPacket, PACKET_OFFSET_IP_DST, PACKET_OFFSET_IP_DST + 4);
		String pktDstIpStr = null;
		try {
			pktDstIpStr = InetAddress.getByAddress(ipDstBytes).getHostAddress();
		} catch (UnknownHostException e) {
			LOG.error("Exception getting Dst IP address [{}]", e.getMessage(), e);
		}
		return pktDstIpStr;
	}


}
