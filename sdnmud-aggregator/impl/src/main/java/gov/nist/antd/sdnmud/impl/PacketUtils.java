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

package gov.nist.antd.sdnmud.impl;

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

	private static final int PACKET_OFFSET_IP = 14;

	private static final int UDP_HEADER_SIZE = 8;

	private static final int TCP_HEADER_SIZE = 20;

	private static final Logger LOG = LoggerFactory.getLogger(PacketUtils.class);

	private PacketUtils() {
		// prohibite to instantiate this class
	}

	/**
	 * Simple internal utility function to convert from a 2-byte array to a short.
	 *
	 * @param bytes byte array
	 * @return the bytes packed into a short
	 */
	private short packShort(byte[] bytes) {
		short val = (short) 0;
		for (int i = 0; i < 2; i++) {
			val <<= 8;
			val |= bytes[i] & 0xff;
		}

		return val;
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
	 * @return the ethertype.
	 */
	private static int extractEtherType(final byte[] payload) {
		byte[] buffer = Arrays.copyOfRange(payload, ETHER_TYPE_START_POSITION, ETHER_TYPE_END_POSITION);
		ByteBuffer wrapped = ByteBuffer.wrap(buffer); // big-endian by default
		short retval = wrapped.asShortBuffer().get();
		int etherType = retval < 0 ? 0xffff + retval + 1 : retval;
		LOG.debug("etherType = :" + etherType);
		return etherType;
	}

	/**
	 * @param rawMac
	 * @return {@link MacAddress} wrapping string value, baked upon binary MAC
	 *         address
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

	/**
	 * get the src MacAddress
	 */
	public static MacAddress extractSrcMacAddress(byte[] payload) {
		byte[] srcMac = extractSrcMac(payload);
		return rawMacToMac(srcMac);
	}

	public static MacAddress extractDstMacAddress(byte[] payload) {
		byte[] dstMac = extractDstMac(payload);
		return rawMacToMac(dstMac);
	}

	/**
	 * Given a raw packet, return the SrcIp.
	 *
	 * @param rawPacket packet
	 * @return srcIp String
	 */
	public static String extractSrcIpStr(final byte[] rawPacket) {
		int etherTYpe = extractEtherType(rawPacket);
		int offset = getPacketOffsetIpSrc(etherTYpe);
		final byte[] ipSrcBytes = Arrays.copyOfRange(rawPacket, offset, offset + 4);
		String pktSrcIpStr = null;
		try {
			pktSrcIpStr = InetAddress.getByAddress(ipSrcBytes).getHostAddress();
		} catch (UnknownHostException e) {
			LOG.error("Exception getting Src IP address [{}]", e.getMessage(), e);
		}
		return pktSrcIpStr;
	}

	/**
	 * Given a raw packet, return the DstIp.
	 *
	 * @param rawPacket packet
	 * @return dstIp String
	 */
	public static String extractDstIpStr(final byte[] rawPacket) {
		int etherType = extractEtherType(rawPacket);
		int offset = getPacketOffsetIpDst(etherType);
		final byte[] ipDstBytes = Arrays.copyOfRange(rawPacket, offset, offset + 4);
		String pktDstIpStr = null;
		try {
			pktDstIpStr = InetAddress.getByAddress(ipDstBytes).getHostAddress();
		} catch (UnknownHostException e) {
			LOG.error("Exception getting Dst IP address [{}]", e.getMessage(), e);
		}
		return pktDstIpStr;
	}

	public static boolean isSYNFlagOnAndACKFlagOff(final byte[] rawPacket) {
		int etherType = extractEtherType(rawPacket);
		int offset = getPacketOffsetTcpSrcPort(etherType);
		byte flags = (byte) (rawPacket[offset + 13] & 63);
		boolean synFlag = (flags & 2) == 2;
		boolean ackFlag = (flags & 0x10) == 0x010;
		return synFlag && !ackFlag;
	}

	public static int getSourcePort(final byte[] rawPacket) {
		int etherType = extractEtherType(rawPacket);
		int offset = getPacketOffsetTcpSrcPort(etherType);
		return (((rawPacket[offset] << 8) & 65280) | (rawPacket[offset + 1] & 255));
	}

	public static int getDestinationPort(final byte[] baseHeader) {
		int etherType = extractEtherType(baseHeader);
		int offset = getPacketOffsetTcpDstPort(etherType);
		return (((baseHeader[offset] << 8) & 65280) | (baseHeader[offset + 1] & 255));

	}

	/**
	 * @param rawPacket
	 * @return
	 */
	public static byte extractIpProtocol(byte[] rawPacket) {
		int etherType = extractEtherType(rawPacket);
		int protoOffset = getPacketOffsetIpProto(etherType);
		final byte[] ipProto = Arrays.copyOfRange(rawPacket, protoOffset, protoOffset + 1);
		return ipProto[0];
	}

	private static int getPacketOffsetIp(int etherType) {
		if (etherType == SdnMudConstants.ETHERTYPE_IPV4)
			return PACKET_OFFSET_IP;
		else if (etherType == SdnMudConstants.ETHERTYPE_CUSTOMER_VLAN)
			return PACKET_OFFSET_IP + 4;
		else {
			LOG.error("Unsupported ethertype ");
			throw new RuntimeException("Unsupported etherType " + etherType);
		}
	}

	private static int getPacketOffsetIpProto(int etherType) {
		return getPacketOffsetIp(etherType) + 9;
	}

	private static int getPacketOffsetIpSrc(int etherType) {
		return getPacketOffsetIp(etherType) + 12;
	}

	private static int getPacketOffsetIpDst(int etherType) {
		return getPacketOffsetIp(etherType) + 16;
	}

	private static int getPacketOffsetTcpSrcPort(int etherType) {
		return getPacketOffsetIp(etherType) + 20;
	}

	private static int getPacketOffsetTcpDstPort(int etherType) {
		return getPacketOffsetTcpSrcPort(etherType) + 2;
	}

	public static byte[] getPacketPayload(byte[] payload, int etherType, int protocol) {
		int end = payload.length;
		int start;
		if (protocol == SdnMudConstants.UDP_PROTOCOL) {
			start = getPacketOffsetIpDst(etherType) + 4+ UDP_HEADER_SIZE;
		} else {
			start = getPacketOffsetIpDst(etherType) + 4+ TCP_HEADER_SIZE;
		}

		return Arrays.copyOfRange(payload, start, end);
	}

}
