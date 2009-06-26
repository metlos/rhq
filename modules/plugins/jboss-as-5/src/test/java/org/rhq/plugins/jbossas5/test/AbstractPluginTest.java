/*
 * Jopr Management Platform
 * Copyright (C) 2005-2009 Red Hat, Inc.
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License, version 2, as
 * published by the Free Software Foundation, and/or the GNU Lesser
 * General Public License, version 2.1, also as published by the Free
 * Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License and the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU General Public License
 * and the GNU Lesser General Public License along with this program;
 * if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */

package org.rhq.plugins.jbossas5.test;

import java.io.File;
import java.util.Set;

import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;

import org.rhq.core.pc.PluginContainer;
import org.rhq.core.pc.PluginContainerConfiguration;
import org.rhq.core.pc.plugin.FileSystemPluginFinder;

/**
 * @author Ian Springer
 */
public abstract class AbstractPluginTest {
    protected static final boolean ENABLE_TESTS = false;
    
    private static final String PLUGIN_NAME = "JBossAS5";

    protected String getPluginName() {
        return PLUGIN_NAME;
    }

    @BeforeSuite
    public void start() {
        try {
            PluginContainerConfiguration pcConfig = new PluginContainerConfiguration();
            File pluginDir = new File("target/itest/plugins");
            pcConfig.setPluginFinder(new FileSystemPluginFinder(pluginDir));
            pcConfig.setPluginDirectory(pluginDir);
            pcConfig.setInsideAgent(false);
            File tmpDir = new File("target/itest/tmp");
            tmpDir.mkdirs();
            pcConfig.setTemporaryDirectory(tmpDir);
            PluginContainer.getInstance().setConfiguration(pcConfig);
            System.out.println("Starting PC...");
            PluginContainer.getInstance().initialize();
            Set<String> pluginNames = PluginContainer.getInstance().getPluginManager().getMetadataManager().getPluginNames();
            System.out.println("PC started with the following plugins: " + pluginNames);
            PluginContainer.getInstance().getInventoryManager().executeServerScanImmediately();
            PluginContainer.getInstance().getInventoryManager().executeServiceScanImmediately();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @AfterSuite
    public void stop() {
        System.out.println("Stopping PC...");
        PluginContainer.getInstance().shutdown();
        System.out.println("PC stopped.");
    }
}