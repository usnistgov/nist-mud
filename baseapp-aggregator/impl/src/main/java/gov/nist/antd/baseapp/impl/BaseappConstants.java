/*
 * Copyright (c) Public Domain
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

package gov.nist.antd.baseapp.impl;

import java.math.BigInteger;

public interface BaseappConstants {

	// Pipeline
	public static final short FIRST_TABLE = 0;
	public static final Short DETECT_EXTERNAL_ARP_TABLE = (short) FIRST_TABLE;
	public static final Short PUSH_VLAN_ON_ARP_TABLE = (short) (FIRST_TABLE + 1);
	public static final Short STRIP_VLAN_TABLE = (short) (FIRST_TABLE + 2);

	public static final Short SRC_DEVICE_MANUFACTURER_STAMP_TABLE = (short) (FIRST_TABLE + 3);
	public static final Short DST_DEVICE_MANUFACTURER_STAMP_TABLE = (short) (FIRST_TABLE + 4);
	public static final Short SDNMUD_RULES_TABLE = (short) (FIRST_TABLE + 5);
	public static final Short PASS_THRU_TABLE = (short) (FIRST_TABLE + 6);
	public static final Short SET_VLAN_RULE_TABLE = (short) (FIRST_TABLE + 7);
	public static final Short UNUSED_ENTRY = (short) (FIRST_TABLE + 8);
	public static final Short L2SWITCH_TABLE = (short) (FIRST_TABLE + 9);
	public static final Short MAX_TID = L2SWITCH_TABLE;
	public static final Short DROP_TABLE = (short) (MAX_TID + 1);

	// Flow table priorities.
	public static final Integer BASE_PRIORITY = 30;
	public static final Integer SEND_PACKET_TO_CONTROLLER_PRIORITY = 0;

	// Flow entry for dropping flows on a match.
	public static final Integer MAX_PRIORITY = BASE_PRIORITY + 20;
	public static final Integer MATCHED_DROP_PACKET_FLOW_PRIORITY_HIGH = BASE_PRIORITY + 15;
	public static final Integer MATCHED_GOTO_FLOW_PRIORITY = BASE_PRIORITY + 10;
	public static final Integer MATCHED_DROP_PACKET_FLOW_PRIORITY = BASE_PRIORITY + 5;
	public static final Integer UNCONDITIONAL_GOTO_PRIORITY = BASE_PRIORITY;
	public static final Integer UNCONDITIONAL_DROP_PRIORITY = BASE_PRIORITY;


	public static final String NONE = "NONE";
	public static final String MUD_FLOW_MISS = "drop";
	public static final String UNCONDITIONAL_GOTO = "UNCONDITIONAL_GOTO";


	public static final String PASSTHRU = "PASSTHRU";

	// Default cache timeout.
	public static final Integer CACHE_TIMEOUT = 120;

	public static final short TCP_PROTOCOL = 6;
	public static final short UDP_PROTOCOL = 17;
	public static final BigInteger DEFAULT_METADATA_MASK = new BigInteger("FFFFFFFFFFFFFFFF",16);


}
