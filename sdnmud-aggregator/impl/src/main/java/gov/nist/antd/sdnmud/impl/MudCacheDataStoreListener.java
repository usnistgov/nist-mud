/*
*
*This program and the accompanying materials are made available under the
*Public Domain.
*
* Copyright (C) 2017 Public Domain.  No rights reserved.
*
* This file includes code developed by employees of the National Institute of
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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.nist.mud.file.cache.rev170915.MudCache;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.nist.mud.file.cache.rev170915.MudCacheEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;


public class MudCacheDataStoreListener implements DataTreeChangeListener<MudCache> {

	private SdnmudProvider sdnmudProvider;

	private HashMap<String, MudCacheEntry> mudCache = new HashMap<String, MudCacheEntry>();
	
	private static final Logger LOG = LoggerFactory.getLogger(MudCacheDataStoreListener.class);

	public MudCacheDataStoreListener(SdnmudProvider sdnmudProvider) {
		this.sdnmudProvider = sdnmudProvider;
	}

	@Override
	public void onDataTreeChanged(Collection<DataTreeModification<MudCache>> changes) {
		this.mudCache.clear();
		for (DataTreeModification<MudCache> change : changes) {
			MudCache cache = change.getRootNode().getDataAfter();
			for (MudCacheEntry cacheEntry : cache.getMudCacheEntries()) {
				String url = cacheEntry.getMudUrl();
				mudCache.put(url, cacheEntry);
			}
		}
	}

	public byte[] getMudFile(String mudUrl) {
		if (this.mudCache.containsKey(mudUrl)) {
			MudCacheEntry cacheEntry = this.mudCache.get(mudUrl);
			if (cacheEntry != null) {
				long timeout = cacheEntry.getCacheTimeout() * 60 * 60 * 1000;
				if (timeout > 0) {
					long retrievalTime = cacheEntry.getRetrivalTime().longValue();
					long currentTime = System.currentTimeMillis();
					if (retrievalTime + timeout < currentTime) {
						return null;
					}
				}

				String fileName = cacheEntry.getCachedMudfileName();
				String pathName = System.getProperty("karaf.home") + "/etc/mudprofiles/" + fileName;
				File mudFile = new File(pathName);
				if (!mudFile.isFile() || !mudFile.exists()) {
					LOG.info("Mud file does not exist in cache");
					return null;
				}

				try {
					RandomAccessFile randomAccessFile = new RandomAccessFile(pathName, "r");
					byte[] mudFileChars = new byte[(int) mudFile.length()];
					randomAccessFile.readFully(mudFileChars, 0, (int) mudFile.length());
					randomAccessFile.close();
					return mudFileChars;
				} catch (IOException ex) {
					LOG.error("IO Exception occured -- can't read MUD file from cache");
					return null;
				}

			} else {
				return null;
			}
		} else {
			return null;
		}
	}

	public void putMudProfileInCache(String mudUrl, long cacheTimeout, String mudProfile) {
		try {
			String[] parts = mudUrl.split(":");
			String uri = parts[1].substring(2);
			String fileName = uri.replace("/", "_");
			String pathName = System.getProperty("karaf.home") + "/etc/mudprofiles/" + fileName;
			File mudFile = new File(pathName);
			FileWriter fileWriter = new FileWriter(mudFile);
			fileWriter.write(mudProfile);
			fileWriter.close();
			ArrayList<HashMap<String, Object>> cacheEntries = new ArrayList<HashMap<String, Object>>();

			for (String url : this.mudCache.keySet()) {
				if (!url.equals(mudUrl)) {
					MudCacheEntry entry = this.mudCache.get(url);
					HashMap jsonObject = new HashMap<String, Object>();
					jsonObject.put("mud-url", entry.getMudUrl());
					jsonObject.put("retrieval-time", entry.getRetrivalTime() );
					jsonObject.put("cache-timeout", entry.getCacheTimeout());
					jsonObject.put("cached-mudfile-name", entry.getCachedMudfileName());
					cacheEntries.add(jsonObject);
				}
			}
			HashMap entry = new HashMap();
			entry.put("mud-url", mudUrl);
			entry.put("retrieval-time",BigInteger.valueOf(System.currentTimeMillis()));
			entry.put("cache-timeout",cacheTimeout);
			entry.put("cached-mudfile-name",fileName);
			cacheEntries.add(entry);
			
			HashMap<String, Object> mudCache = new HashMap<>();
			mudCache.put("mud-cache", cacheEntries);
			String jsonString = new Gson().toJson(mudCache);
			sdnmudProvider.getDatastoreUpdater().writeToDatastore(jsonString, MudCache.QNAME);
			
		} catch (Exception ex) {
			LOG.error("Error updating the cache " + mudUrl);

		}

	}

}
