/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package gov.nist.antd.vlan.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.Table;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.TableKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;

/**
 * Flow commit wrapper.
 */
class FlowCommitWrapper {
    private static final Logger LOG = LoggerFactory.getLogger(FlowCommitWrapper.class);

    private DataBroker dataBrokerService;



    public FlowCommitWrapper(DataBroker dataBrokerService) {
        this.dataBrokerService = dataBrokerService;
    }

    public CheckedFuture<Void, TransactionCommitFailedException> writeFlowToConfig(
            InstanceIdentifier<Flow> flowPath, Flow flowBody) {
        ReadWriteTransaction addFlowTransaction = dataBrokerService.newReadWriteTransaction();
        // mdsalApiManager.addFlowToTx(dpnId, flowBody, addFlowTransaction);
        addFlowTransaction.merge(LogicalDatastoreType.CONFIGURATION, flowPath, flowBody, true);
        return addFlowTransaction.submit();
    }

    private boolean deleteFlow(FlowKey flowKey, short tableId,
            InstanceIdentifier<FlowCapableNode> flowNodeIdent) {
        ReadWriteTransaction modification = dataBrokerService.newReadWriteTransaction();
        LOG.info("deleteFlow : " + flowKey.getId().getValue());
        final InstanceIdentifier<Flow> path =
                flowNodeIdent.child(Table.class, new TableKey(tableId)).child(Flow.class, flowKey);
        modification.delete(LogicalDatastoreType.CONFIGURATION, path);
        CheckedFuture<Void, TransactionCommitFailedException> commitFuture = modification.submit();
        try {
            commitFuture.checkedGet();
        } catch (TransactionCommitFailedException e) {
            return false;
        }
        return true;
    }

    boolean flowExists(String flowIdPrefix, short tableId, InstanceIdentifier<FlowCapableNode> flowNodeIdent) {
        InstanceIdentifier<Table> tableInstanceId =
                flowNodeIdent.child(Table.class, new TableKey(tableId));
        CheckedFuture<Optional<Table>, ReadFailedException> commitFuture = dataBrokerService
                .newReadOnlyTransaction().read(LogicalDatastoreType.CONFIGURATION, tableInstanceId);
        try {
            Set<Table> tableSet = commitFuture.get().asSet();
            for (Table table : tableSet) {
                List<Flow> flows = table.getFlow();
                for (Flow flow: flows) {
                    if (flow.getId().getValue().startsWith(flowIdPrefix)){
                        return true;
                    }
                }
            }
            return false;
        } catch (InterruptedException e) {
            LOG.error("Error reading flows ", e);
            return false;
        } catch (ExecutionException e) {
            LOG.error("Error reading flows ", e);
            return false;
        }
    }

    private List<FlowKey> readFlows(InstanceIdentifier<FlowCapableNode> flowNodeIdent, short tableId,
            String uriPrefix, MacAddress sourceMacAddress) {

        List<FlowKey> retval = new ArrayList<FlowKey>();

        InstanceIdentifier<Table> tableInstanceId =
                flowNodeIdent.child(Table.class, new TableKey(tableId));
        CheckedFuture<Optional<Table>, ReadFailedException> commitFuture = dataBrokerService
                .newReadOnlyTransaction().read(LogicalDatastoreType.CONFIGURATION, tableInstanceId);
        try {
            Set<Table> tableSet = commitFuture.get().asSet();
            for (Table table : tableSet) {
                List<Flow> flows = table.getFlow();
                for (Flow flow : flows) {
                    String flowId = flow.getId().getValue();
                    if (sourceMacAddress != null) {
                        MacAddress flowSourceMacAddress = null;

                        if (flow.getMatch() != null && flow.getMatch().getEthernetMatch() != null
                                && flow.getMatch().getEthernetMatch().getEthernetSource() != null) {
                            flowSourceMacAddress =
                                    flow.getMatch().getEthernetMatch().getEthernetSource().getAddress();
                        }

                        if (flowSourceMacAddress != null && flowId.startsWith(uriPrefix)
                                && flowSourceMacAddress.equals(sourceMacAddress)) {
                            retval.add(flow.getKey());
                        }
                    } else {
                        if (flowId.startsWith(uriPrefix)) {
                            retval.add(flow.getKey());
                        }
                    }
                }
            }
            return retval;
        } catch (InterruptedException e) {
            LOG.error("Error reading flows ", e);
            return null;
        } catch (ExecutionException e) {
            LOG.error("Error reading flows ", e);
            return null;
        }
    }

    synchronized void writeFlow(FlowBuilder flow,
            InstanceIdentifier<FlowCapableNode> flowNodeIdent) {

        ReadWriteTransaction modification = dataBrokerService.newReadWriteTransaction();
        LOG.info("writeFlow : " + flowNodeIdent + " Flow : " + flow.getFlowName() + " tableId "
                + flow.getTableId() + " flowId " + flow.getId().getValue());

        LOG.info(flow.toString());

        final InstanceIdentifier<Flow> path1 = flowNodeIdent
                .child(Table.class, new TableKey(flow.getTableId())).child(Flow.class, flow.getKey());

        modification.merge(LogicalDatastoreType.CONFIGURATION, path1, flow.build(), true);
        modification.submit();
    }

    /**
     * Delete the flows corresponding to a MUD uri and sourceMacAddress.
     *
     * @param flowCapableNode -- the node from which to delete the flows.
     * @param uri -- the mud URI
     * @param sourceMacAddress -- the device source mac address.
     */

    synchronized void deleteFlows(InstanceIdentifier<FlowCapableNode> flowCapableNode,
            String uri, short table, MacAddress sourceMacAddress) {

        LOG.info("deleteFlows " + uri);
        List<FlowKey> flowKeys = readFlows(flowCapableNode, table, uri, sourceMacAddress);
        for (FlowKey flowKey : flowKeys) {
            deleteFlow(flowKey, table, flowCapableNode);
        }
    }





}
