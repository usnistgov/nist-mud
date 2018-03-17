/*
 * Copyright © 2017 MIST and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package gov.nist.antd.baseapp.cli.impl;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import gov.nist.antd.baseapp.cli.api.BaseappCliCommands;

public class BaseappCliCommandsImpl implements BaseappCliCommands {

    private static final Logger LOG = LoggerFactory.getLogger(BaseappCliCommandsImpl.class);
    private final DataBroker dataBroker;

    public BaseappCliCommandsImpl(final DataBroker db) {
        this.dataBroker = db;
        LOG.info("BaseappCliCommandImpl initialized");
    }

    @Override
    public Object testCommand(Object testArgument) {
        return "This is a test implementation of test-command";
    }
}
