/*
 * Copyright © None.
 *
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package gov.nist.antd.sdnmud.cli.commands;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.nist.antd.sdnmud.cli.api.SdniotCliCommands;

/**
 * This is an example class. The class name can be renamed to match the command implementation that it will invoke.
 * Specify command details by updating the fields in the Command annotation below.
 */
@Command(name = "test-command", scope = "add the scope of the command, usually project name", description = "add a description for the command")
public class SdniotCliTestCommand implements Action {



    private static final Logger LOG = LoggerFactory.getLogger(SdniotCliTestCommand.class);
    @Argument(index = 0, name = "testArgument", description = "The command argument", required = false, multiValued = false)

    private String testArgument;

    protected final SdniotCliCommands service;

    public SdniotCliTestCommand(final SdniotCliCommands service) {
        this.service = service;
    }




    @Override
    public Object execute() throws Exception {
        final String testMessage = (String) service.testCommand(testArgument);
        return testMessage;
    }
}