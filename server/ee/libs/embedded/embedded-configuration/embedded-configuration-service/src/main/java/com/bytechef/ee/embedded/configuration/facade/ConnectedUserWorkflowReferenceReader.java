/*
 * Copyright 2025 ByteChef
 *
 * Licensed under the ByteChef Enterprise license (the "Enterprise License");
 * you may not use this file except in compliance with the Enterprise License.
 */

package com.bytechef.ee.embedded.configuration.facade;

import com.bytechef.automation.configuration.domain.ProjectWorkflow;
import com.bytechef.automation.configuration.service.ProjectDeploymentService;
import com.bytechef.automation.configuration.service.ProjectDeploymentWorkflowService;
import com.bytechef.automation.configuration.service.ProjectWorkflowService;
import com.bytechef.ee.embedded.configuration.domain.ConnectedUserProject;
import com.bytechef.ee.embedded.configuration.domain.ConnectedUserProjectWorkflow;
import com.bytechef.ee.embedded.configuration.dto.ConnectedUserWorkflowCopyDTO;
import com.bytechef.ee.embedded.configuration.dto.ConnectedUserWorkflowReferenceDTO;
import com.bytechef.ee.embedded.configuration.repository.ConnectedUserProjectWorkflowRepository;
import com.bytechef.ee.embedded.configuration.service.ConnectedUserProjectService;
import com.bytechef.ee.embedded.configuration.service.ConnectedUserProjectWorkflowService;
import com.bytechef.ee.embedded.connected.user.domain.ConnectedUser;
import com.bytechef.ee.embedded.connected.user.service.ConnectedUserService;
import com.bytechef.platform.annotation.ConditionalOnEEVersion;
import com.bytechef.platform.configuration.domain.Environment;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * @version ee
 *
 * @author Ivica Cardic
 */
@Component
@ConditionalOnEEVersion
public class ConnectedUserWorkflowReferenceReader {

    private final ConnectedUserProjectService connectedUserProjectService;
    private final ConnectedUserProjectWorkflowRepository connectedUserProjectWorkflowRepository;
    private final ConnectedUserProjectWorkflowService connectedUserProjectWorkflowService;
    private final ConnectedUserService connectedUserService;
    private final ProjectDeploymentService projectDeploymentService;
    private final ProjectDeploymentWorkflowService projectDeploymentWorkflowService;
    private final ProjectWorkflowService projectWorkflowService;

    @SuppressFBWarnings("EI")
    public ConnectedUserWorkflowReferenceReader(
        ConnectedUserProjectService connectedUserProjectService,
        ConnectedUserProjectWorkflowRepository connectedUserProjectWorkflowRepository,
        ConnectedUserProjectWorkflowService connectedUserProjectWorkflowService,
        ConnectedUserService connectedUserService, ProjectDeploymentService projectDeploymentService,
        ProjectDeploymentWorkflowService projectDeploymentWorkflowService,
        ProjectWorkflowService projectWorkflowService) {

        this.connectedUserProjectService = connectedUserProjectService;
        this.connectedUserProjectWorkflowRepository = connectedUserProjectWorkflowRepository;
        this.connectedUserProjectWorkflowService = connectedUserProjectWorkflowService;
        this.connectedUserService = connectedUserService;
        this.projectDeploymentService = projectDeploymentService;
        this.projectDeploymentWorkflowService = projectDeploymentWorkflowService;
        this.projectWorkflowService = projectWorkflowService;
    }

    public List<ConnectedUserWorkflowCopyDTO> getCopies(Set<String> automationUuids, Environment environment) {
        List<ConnectedUserProjectWorkflow> copies =
            connectedUserProjectWorkflowService.getCopiedConnectedUserProjectWorkflows(automationUuids);

        List<Long> connectedUserProjectIds = copies.stream()
            .map(ConnectedUserProjectWorkflow::getConnectedUserProjectId)
            .distinct()
            .toList();

        List<ConnectedUserProject> connectedUserProjects =
            connectedUserProjectService.getConnectedUserProjects(connectedUserProjectIds);

        List<Long> connectedUserIds = connectedUserProjects.stream()
            .map(ConnectedUserProject::getConnectedUserId)
            .distinct()
            .toList();

        Map<Long, ConnectedUser> connectedUserMap = connectedUserService.getConnectedUsers(connectedUserIds)
            .stream()
            .collect(Collectors.toMap(ConnectedUser::getId, Function.identity()));

        Map<Long, ConnectedUser> connectedUsersByConnectedUserProjectId = connectedUserProjects.stream()
            .collect(Collectors.toMap(
                ConnectedUserProject::getId,
                connectedUserProject -> connectedUserMap.get(connectedUserProject.getConnectedUserId())));

        return copies.stream()
            .filter(copy -> {
                ConnectedUser connectedUser = connectedUsersByConnectedUserProjectId.get(
                    copy.getConnectedUserProjectId());

                return connectedUser.getEnvironment() == environment;
            })
            .map(copy -> toCopyDTO(
                copy, connectedUsersByConnectedUserProjectId.get(copy.getConnectedUserProjectId()), environment))
            .toList();
    }

    public List<ConnectedUserWorkflowReferenceDTO> getReferences(Set<String> automationUuids) {
        List<ConnectedUserProjectWorkflow> references =
            connectedUserProjectWorkflowRepository.findAllByAutomationUuidIn(automationUuids);

        List<Long> connectedUserProjectIds = references.stream()
            .map(ConnectedUserProjectWorkflow::getConnectedUserProjectId)
            .distinct()
            .toList();

        Map<Long, ConnectedUserProject> connectedUserProjectMap =
            connectedUserProjectService.getConnectedUserProjects(connectedUserProjectIds)
                .stream()
                .collect(Collectors.toMap(ConnectedUserProject::getId, Function.identity()));

        List<Long> connectedUserIds = connectedUserProjectMap.values()
            .stream()
            .map(ConnectedUserProject::getConnectedUserId)
            .distinct()
            .toList();

        Map<Long, ConnectedUser> connectedUserMap = connectedUserService.getConnectedUsers(connectedUserIds)
            .stream()
            .collect(Collectors.toMap(ConnectedUser::getId, Function.identity()));

        List<Long> projectIds = connectedUserProjectMap.values()
            .stream()
            .map(ConnectedUserProject::getProjectId)
            .distinct()
            .toList();

        Map<Long, String> workflowIds = projectWorkflowService.getProjectWorkflows(projectIds)
            .stream()
            .collect(Collectors.toMap(ProjectWorkflow::getId, ProjectWorkflow::getWorkflowId));

        return references.stream()
            .map(reference -> toDTO(reference, connectedUserProjectMap, connectedUserMap, workflowIds))
            .toList();
    }

    private boolean isCopyEnabled(ProjectWorkflow projectWorkflow, Environment environment) {
        return projectDeploymentService.fetchProjectDeployment(projectWorkflow.getProjectId(), environment)
            .flatMap(projectDeployment -> projectWorkflowService
                .fetchProjectWorkflowWorkflowId(projectDeployment.getId(), projectWorkflow.getUuidAsString())
                .map(workflowId -> projectDeploymentWorkflowService.isProjectDeploymentWorkflowEnabled(
                    projectDeployment.getId(), workflowId)))
            .orElse(false);
    }

    private ConnectedUserWorkflowCopyDTO toCopyDTO(
        ConnectedUserProjectWorkflow copy, ConnectedUser connectedUser, Environment environment) {

        ProjectWorkflow projectWorkflow = projectWorkflowService.getProjectWorkflow(
            Objects.requireNonNull(copy.getProjectWorkflowId()));

        return new ConnectedUserWorkflowCopyDTO(
            copy.getCopiedFromWorkflowUuid(), projectWorkflow.getUuidAsString(), connectedUser.getExternalId(),
            connectedUser.getName(), connectedUser.getEmail(), isCopyEnabled(projectWorkflow, environment));
    }

    private static ConnectedUserWorkflowReferenceDTO toDTO(
        ConnectedUserProjectWorkflow reference, Map<Long, ConnectedUserProject> connectedUserProjectMap,
        Map<Long, ConnectedUser> connectedUserMap, Map<Long, String> workflowIds) {

        ConnectedUserProject connectedUserProject = connectedUserProjectMap.get(
            reference.getConnectedUserProjectId());

        ConnectedUser connectedUser = connectedUserMap.get(connectedUserProject.getConnectedUserId());

        Environment environment = connectedUser.getEnvironment();

        return new ConnectedUserWorkflowReferenceDTO(
            reference.getAutomationUuid(), connectedUser.getId(), connectedUser.getExternalId(),
            environment.name(), reference.isEnabled(), reference.isDangling(), reference.getDanglingReason(),
            workflowIds.get(reference.getProjectWorkflowId()), reference.getProjectDeploymentId());
    }
}
