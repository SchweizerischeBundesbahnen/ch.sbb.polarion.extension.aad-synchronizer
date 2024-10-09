package ch.sbb.polarion.extension.aad.synchronizer.service;

import com.polarion.alm.projects.IProjectService;
import com.polarion.platform.security.ISecurityService;

import java.util.List;

public interface IPolarionServiceFactory {
    IPolarionService createPolarionService(ISecurityService securityService, IProjectService projectService, boolean dryRun, List<String> memberIds);
}
