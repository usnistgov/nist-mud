package gov.nist.antd.vlan.impl;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.nist.openstack.rev180715.NistOpenstackService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.nist.openstack.rev180715.VnfReadyInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.nist.openstack.rev180715.VnfReadyOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.nist.openstack.rev180715.VnfReadyOutputBuilder;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NistOpenstackServiceImpl implements NistOpenstackService {
    
    private static final Logger LOG = LoggerFactory
            .getLogger(NistOpenstackServiceImpl.class);


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

    private VlanProvider vlanProvider;

    public NistOpenstackServiceImpl(VlanProvider vlanProvider) {
        this.vlanProvider  = vlanProvider;
    }

    @Override
    public Future<RpcResult<VnfReadyOutput>> vnfReady(VnfReadyInput input) {
        String stackId = input.getStackId();
        Long vlanId = input.getVlanId();
        String switchDpnId = input.getVnfSwitchDpnId();
        VnfReadyOutputBuilder vnfrob = new VnfReadyOutputBuilder();
        vnfrob.setStatusCode(0);
        LOG.info("vnfReady : " + stackId + " vlanId " +  vlanId + " dpnId " +  switchDpnId);
        return new CompletedFuture<RpcResult<VnfReadyOutput>>(RpcResultBuilder.success (vnfrob).build());
        
    }

}
