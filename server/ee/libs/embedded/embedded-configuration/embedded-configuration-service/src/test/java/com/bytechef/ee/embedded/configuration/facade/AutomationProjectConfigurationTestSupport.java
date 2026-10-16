/*
 * Copyright 2025 ByteChef
 *
 * Licensed under the ByteChef Enterprise license (the "Enterprise License");
 * you may not use this file except in compliance with the Enterprise License.
 */

package com.bytechef.ee.embedded.configuration.facade;

import com.bytechef.ee.embedded.configuration.domain.AutomationProjectConfiguration;
import com.bytechef.ee.embedded.configuration.dto.AutomationProjectDTO;
import com.bytechef.ee.embedded.configuration.service.AutomationProjectConfigurationService;
import com.bytechef.platform.configuration.domain.Environment;
import java.util.List;
import java.util.Optional;

/**
 * @version ee
 *
 * @author Ivica Cardic
 */
public final class AutomationProjectConfigurationTestSupport {

    private AutomationProjectConfigurationTestSupport() {
    }

    public static void publishAndPinProject(
        AutomationProjectFacade automationProjectFacade,
        AutomationProjectConfigurationService automationProjectConfigurationService, long projectId) {

        publishAndPinProject(
            automationProjectFacade, automationProjectConfigurationService, projectId, List.of(Environment.PRODUCTION));
    }

    public static void publishAndPinProject(
        AutomationProjectFacade automationProjectFacade,
        AutomationProjectConfigurationService automationProjectConfigurationService, long projectId,
        List<Environment> environments) {

        automationProjectFacade.publishProject(projectId, null);

        int publishedVersion = automationProjectFacade.getPublishedProjects()
            .stream()
            .filter(project -> project.id() == projectId)
            .map(AutomationProjectDTO::lastPublishedVersion)
            .findFirst()
            .orElseThrow();

        for (Environment environment : environments) {
            pinProject(automationProjectConfigurationService, projectId, publishedVersion, environment);
        }
    }

    private static void pinProject(
        AutomationProjectConfigurationService automationProjectConfigurationService, long projectId,
        int publishedVersion, Environment environment) {

        Optional<AutomationProjectConfiguration> configurationOptional =
            automationProjectConfigurationService.getConfigurations(projectId, environment.ordinal())
                .stream()
                .findFirst();

        if (configurationOptional.isPresent()) {
            AutomationProjectConfiguration existingConfiguration = configurationOptional.get();

            automationProjectConfigurationService.update(
                existingConfiguration.getId(), existingConfiguration.getName(),
                existingConfiguration.getPermissionExpression(), publishedVersion, true, true);
        } else {
            automationProjectConfigurationService.create(
                projectId, environment.ordinal(), "Default", null, publishedVersion, true, true);
        }
    }
}
