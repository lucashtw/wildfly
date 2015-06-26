/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 2110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.test.shared;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.xnio.IoUtils;

/**
 * @author Stuart Douglas
 */
public class ServerReload {

    public static final int TIMEOUT = 100000;

    public static void executeReloadAndWaitForCompletion(ModelControllerClient client) {
        executeReloadAndWaitForCompletion(client, TIMEOUT);
    }

    public static void executeReloadAndWaitForCompletion(ModelControllerClient client, boolean adminOnly) {
        executeReloadAndWaitForCompletion(client, TIMEOUT, adminOnly, null, -1);
    }

    public static void executeReloadAndWaitForCompletion(ModelControllerClient client, int timeout) {
        executeReloadAndWaitForCompletion(client, timeout, false, null, -1);
    }

    /**
     *
     * @param client
     * @param timeout
     * @param adminOnly if {@code true}, the server will be reloaded in admin-only mode
     * @param serverAddress if {@code null}, use {@code TestSuiteEnvironment.getServerAddress()} to create the ModelControllerClient
     * @param serverPort if {@code -1}, use {@code TestSuiteEnvironment.getServerPort()} to create the ModelControllerClient
     */
    public static void executeReloadAndWaitForCompletion(ModelControllerClient client, int timeout, boolean adminOnly, String serverAddress, int serverPort) {
        executeReload(client, adminOnly);
        waitForLiveServerToReload(timeout,
                serverAddress != null ? serverAddress : TestSuiteEnvironment.getServerAddress(),
                serverPort != -1 ? serverPort : TestSuiteEnvironment.getServerPort());
    }

    private static void executeReload(ModelControllerClient client, boolean adminOnly) {
        ModelNode operation = new ModelNode();
        operation.get(OP_ADDR).setEmptyList();
        operation.get(OP).set("reload");
        operation.get("admin-only").set(adminOnly);
        try {
            ModelNode result = client.execute(operation);
            Assert.assertEquals("success", result.get(ClientConstants.OUTCOME).asString());
        } catch(IOException e) {
            final Throwable cause = e.getCause();
            if (!(cause instanceof ExecutionException) && !(cause instanceof CancellationException)) {
                throw new RuntimeException(e);
            } // else ignore, this might happen if the channel gets closed before we got the response
        }
    }

    private static void waitForLiveServerToReload(int timeout, String serverAddress, int serverPort) {
        long start = System.currentTimeMillis();
        ModelNode operation = new ModelNode();
        operation.get(OP_ADDR).setEmptyList();
        operation.get(OP).set(READ_ATTRIBUTE_OPERATION);
        operation.get(NAME).set("server-state");
        while (System.currentTimeMillis() - start < timeout) {
            try {
                ModelControllerClient liveClient = ModelControllerClient.Factory.create(
                        serverAddress, serverPort);
                try {
                    ModelNode result = liveClient.execute(operation);
                    if ("running" .equals(result.get(RESULT).asString())) {
                        return;
                    }
                } catch (IOException e) {
                } finally {
                    IoUtils.safeClose(liveClient);
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                }
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            }
        }
        fail("Live Server did not reload in the imparted time.");
    }
}
