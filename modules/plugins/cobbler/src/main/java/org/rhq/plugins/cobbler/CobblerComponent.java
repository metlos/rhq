/*
 * RHQ Management Platform
 * Copyright (C) 2005-2009 Red Hat, Inc.
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package org.rhq.plugins.cobbler;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.HashSet;
import java.util.Set;

import net.augeas.Augeas;

import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.rhq.core.domain.configuration.Configuration;
import org.rhq.core.domain.configuration.RawConfiguration;
import org.rhq.core.domain.measurement.AvailabilityType;
import org.rhq.core.pluginapi.configuration.ResourceConfigurationFacet;
import org.rhq.core.pluginapi.inventory.InvalidPluginConfigurationException;
import org.rhq.core.pluginapi.inventory.ResourceContext;
import org.rhq.plugins.augeas.AugeasConfigurationComponent;
import org.rhq.plugins.augeas.helper.AugeasRawConfigHelper;

/**
 * The ResourceComponent for the "Cobbler File" ResourceType.
 *
 * @author Ian Springer
 */
public class CobblerComponent extends AugeasConfigurationComponent implements ResourceConfigurationFacet {
    private static final String MODULES_PATH = "/etc/cobbler/modules.conf";
    private static final String SETTINGS_PATH = "/etc/cobbler/settings";
    private final Log log = LogFactory.getLog(this.getClass());
    private AugeasRawConfigHelper rawConfigHelper;

    @Override
    public void start(ResourceContext resourceContext) throws InvalidPluginConfigurationException, Exception {
        super.start(resourceContext);
        rawConfigHelper = new AugeasRawConfigHelper(getAugeasRootPath(), AUGEAS_LOAD_PATH,
            getResourceConfigurationRootPath());
        rawConfigHelper.addLens("CobblerSettings.lns", SETTINGS_PATH);
        rawConfigHelper.addLens("CobblerModules.lns", MODULES_PATH);

        rawConfigHelper.addNode(SETTINGS_PATH, "server");
        rawConfigHelper.addNode(SETTINGS_PATH, "next_server");
        rawConfigHelper.addNode(SETTINGS_PATH, "http_port");
        rawConfigHelper.addNode(SETTINGS_PATH, "default_kickstart");
        rawConfigHelper.addNode(SETTINGS_PATH, "snippetsdir");

        //        rawConfigHelper.addNode(SETTINGS_PATH, "manage_dhcp");
        //        rawConfigHelper.addNode(SETTINGS_PATH, "manage_dns");
        rawConfigHelper.addNode(SETTINGS_PATH, "manage_reverse_zones");
        rawConfigHelper.addNode(SETTINGS_PATH, "manage_forward_zones");

        rawConfigHelper.addNode(SETTINGS_PATH, "default_virt_bridge");
        rawConfigHelper.addNode(SETTINGS_PATH, "default_virt_file_size");
        rawConfigHelper.addNode(SETTINGS_PATH, "default_virt_ram");
        rawConfigHelper.addNode(SETTINGS_PATH, "default_virt_type");

        rawConfigHelper.addNode(MODULES_PATH, "authentication/module");
        rawConfigHelper.addNode(MODULES_PATH, "authorization/module");
        rawConfigHelper.addNode(MODULES_PATH, "dhcp/module");
        rawConfigHelper.addNode(MODULES_PATH, "dns/module");

    }

    @Override
    protected void setupAugeasModules(Augeas augeas) {
        augeas.set("/augeas/load/CobblerSettings/lens", "CobblerSettings.lns");
        augeas.set("/augeas/load/CobblerSettings/incl[1]", SETTINGS_PATH);
        augeas.set("/augeas/load/CobblerModules/lens", "CobblerModules.lns");
        augeas.set("/augeas/load/CobblerModules/incl[1]", MODULES_PATH);
    }

    public AvailabilityType getAvailability() {
        return super.getAvailability();
    }

    public Set<RawConfiguration> loadRawConfigurations() {
        try {
            Set<RawConfiguration> configs = new HashSet<RawConfiguration>();
            RawConfiguration modules = new RawConfiguration();
            modules.setPath(MODULES_PATH);
            modules.setContents(FileUtils.readFileToByteArray(new File(MODULES_PATH)));
            configs.add(modules);

            RawConfiguration settings = new RawConfiguration();
            settings.setPath(SETTINGS_PATH);
            settings.setContents(FileUtils.readFileToByteArray(new File(SETTINGS_PATH)));
            configs.add(settings);
            return configs;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Configuration loadStructuredConfiguration() {
        try {
            return loadResourceConfiguration();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public RawConfiguration mergeRawConfiguration(Configuration from, RawConfiguration to) {
        try {
            RawConfiguration config = new RawConfiguration();
            rawConfigHelper.mergeRawConfig(from, to, config);
            return config;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void mergeStructuredConfiguration(RawConfiguration from, Configuration to) {
        try {
            rawConfigHelper.mergeStructured(from, to);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    public void persistRawConfiguration(RawConfiguration rawConfiguration) {
        try {
            rawConfigHelper.save(rawConfiguration);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void persistStructuredConfiguration(Configuration configuration) {
        try {
            updateStructuredConfiguration(configuration);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void validateRawConfiguration(RawConfiguration rawConfiguration) {
        // TODO Auto-generated method stub
    }

    public void validateStructuredConfiguration(Configuration configuration) {
        // TODO Auto-generated method stub
    }

    private String normalizeToUnix(byte[] contents) throws UnsupportedEncodingException {
        String s = new String(contents, "UTF8");
        return s.replaceAll("\r\n", "\n");
    }
}
