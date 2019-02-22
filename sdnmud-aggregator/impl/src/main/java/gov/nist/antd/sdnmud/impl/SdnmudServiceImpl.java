/*
 * Copyright (c) Public Domain Jun 27, 2018.
 * This code is released to the public domain in accordance with the following disclaimer:
 *
 * "This software was developed at the National Institute of Standards
 * and Technology by employees of the Federal Government in the course of
 * their official duties. Pursuant to title 17 Section 105 of the United
 * States Code this software is not subject to copyright protection and is
 * in the public domain. It is an experimental system. NIST assumes no responsibility
 * whatsoever for its use by other parties, and makes no guarantees, expressed or
 * implied, about its quality, reliability, or any other characteristic. We would
 * appreciate acknowledgement if the software is used. This software can be redistributed
 * and/or modified freely provided that any derivative works bear
 * some notice that they are derived from it, and any modified versions bear some
 * notice that they have been modified."
 */

package gov.nist.antd.sdnmud.impl;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.ArrayList;

import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.ClearCacheOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.ClearCacheOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.ClearMudRulesOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.ClearMudRulesOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.ClearPacketCountOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.ClearPacketCountOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.GetPacketCountOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.GetPacketCountOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.GetMudUnmappedAddressesOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.GetMudUnmappedAddressesOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.rev170915.SdnmudService;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;

/**
 * @author mranga@nist.gov
 *
 */
public class SdnmudServiceImpl implements SdnmudService {

	class CompletedFuture<T> implements Future<T> {
		private final T result;

		public CompletedFuture(final T result) {
			this.result = result;
		}

		@Override
		public boolean cancel(final boolean b) {
			return false;
		}

		@Override
		public boolean isCancelled() {
			return false;
		}

		@Override
		public boolean isDone() {
			return true;
		}

		@Override
		public T get() throws InterruptedException, ExecutionException {
			return this.result;
		}

		@Override
		public T get(final long l, final TimeUnit timeUnit)
				throws InterruptedException, ExecutionException, TimeoutException {
			return get();
		}

	}

	private SdnmudProvider sdnmudProvider;

	public SdnmudServiceImpl(SdnmudProvider sdnmudProvider) {
		this.sdnmudProvider = sdnmudProvider;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.
	 * rev170915.SdnmudService#getPacketCount()
	 */
	@Override
	public Future<RpcResult<GetPacketCountOutput>> getPacketCount() {
		GetPacketCountOutputBuilder gpcob = new GetPacketCountOutputBuilder();
		gpcob.setPacketCount(new Long(sdnmudProvider.getPacketInDispatcher().getPacketInCount(false)));
		gpcob.setMudPacketCount(new Long(sdnmudProvider.getPacketInDispatcher().getMudPacketInCount(false)));
		RpcResult<GetPacketCountOutput> result = RpcResultBuilder.success(gpcob).build();
		return new CompletedFuture<RpcResult<GetPacketCountOutput>>(result);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.
	 * rev170915.SdnmudService#clearPacketCount()
	 */
	@Override
	public Future<RpcResult<ClearPacketCountOutput>> clearPacketCount() {
		ClearPacketCountOutputBuilder cpcob = new ClearPacketCountOutputBuilder();
		sdnmudProvider.getPacketInDispatcher().clearPacketInCount();
		cpcob.setSuccess(true);
		RpcResult<ClearPacketCountOutput> result = RpcResultBuilder.success(cpcob).build();
		return new CompletedFuture<RpcResult<ClearPacketCountOutput>>(result);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.sdnmud.
	 * rev170915.SdnmudService#clearCache()
	 */
	@Override
	public Future<RpcResult<ClearCacheOutput>> clearCache() {

		ClearCacheOutputBuilder cpcob = new ClearCacheOutputBuilder();
		sdnmudProvider.getPacketInDispatcher().clearMfgModelRules();
		cpcob.setSuccess(true);
		RpcResult<ClearCacheOutput> result = RpcResultBuilder.success(cpcob).build();
		return new CompletedFuture<RpcResult<ClearCacheOutput>>(result);
	}

	@Override
	public Future<RpcResult<ClearMudRulesOutput>> clearMudRules() {
		ClearMudRulesOutputBuilder cmrob = new ClearMudRulesOutputBuilder();
		sdnmudProvider.clearMudRules();
		sdnmudProvider.getMudFlowsInstaller().clearMudRules();
		sdnmudProvider.getStateChangeScanner().clearState();
		sdnmudProvider.getMappingDataStoreListener().clearState();
	
		cmrob.setSuccess(true);
		RpcResult<ClearMudRulesOutput> result = RpcResultBuilder.success(cmrob).build();
		return new CompletedFuture<RpcResult<ClearMudRulesOutput>>(result);
	}

    @Override
    public Future<RpcResult<GetMudUnmappedAddressesOutput>> getMudUnmappedAddresses() {
        GetMudUnmappedAddressesOutputBuilder guaob = new GetMudUnmappedAddressesOutputBuilder();
        ArrayList addrList = new ArrayList(sdnmudProvider.getPacketInDispatcher().getUnclassifiedMacAddresses());
        guaob.setUnmappedDeviceAddresses(addrList);
		RpcResult<GetMudUnmappedAddressesOutput> result = RpcResultBuilder.success(guaob).build();
        return new CompletedFuture<RpcResult<GetMudUnmappedAddressesOutput>>(result);
    }
	
	

}
