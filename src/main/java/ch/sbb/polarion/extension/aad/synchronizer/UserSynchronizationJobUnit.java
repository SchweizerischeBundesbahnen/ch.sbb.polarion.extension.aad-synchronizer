package ch.sbb.polarion.extension.aad.synchronizer;

import ch.sbb.polarion.extension.aad.synchronizer.connector.GraphConnector;
import ch.sbb.polarion.extension.aad.synchronizer.connector.IGraphConnector;
import ch.sbb.polarion.extension.aad.synchronizer.exception.NotFoundException;
import ch.sbb.polarion.extension.aad.synchronizer.filter.Blacklist;
import ch.sbb.polarion.extension.aad.synchronizer.filter.MemberFilter;
import ch.sbb.polarion.extension.aad.synchronizer.filter.Whitelist;
import ch.sbb.polarion.extension.aad.synchronizer.service.GraphService;
import ch.sbb.polarion.extension.aad.synchronizer.service.IGraphService;
import ch.sbb.polarion.extension.aad.synchronizer.service.IPolarionService;
import ch.sbb.polarion.extension.aad.synchronizer.service.IPolarionServiceFactory;
import ch.sbb.polarion.extension.aad.synchronizer.service.PolarionService;
import ch.sbb.polarion.extension.aad.synchronizer.utils.OAuth2Client;
import ch.sbb.polarion.extension.aad.synchronizer.utils.OSGiUtils;
import ch.sbb.polarion.extension.generic.util.JobLogger;
import com.polarion.alm.projects.IProjectService;
import com.polarion.alm.shared.api.transaction.TransactionalExecutor;
import com.polarion.core.config.ILoginSecurityConfiguration;
import com.polarion.core.config.IOAuth2SecurityConfiguration;
import com.polarion.platform.core.PlatformContext;
import com.polarion.platform.jobs.IJobStatus;
import com.polarion.platform.jobs.IJobUnitFactory;
import com.polarion.platform.jobs.IProgressMonitor;
import com.polarion.platform.jobs.spi.AbstractJobUnit;
import com.polarion.platform.security.ISecurityService;
import com.polarion.platform.security.auth.AuthenticationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;

public class UserSynchronizationJobUnit extends AbstractJobUnit implements AADUserSynchronizationJobUnit {
    private final ISecurityService securityService;
    private final IProjectService projectService;
    private final IGraphConnector externalGraphConnector;

    private String authenticationProviderId;
    private IOAuth2SecurityConfiguration authenticationProviderConfiguration;
    private String groupPrefix;
    private Whitelist whitelist;
    private Blacklist blacklist;
    private boolean dryRun = false;
    private boolean checkLastSynchronization = false;

    public UserSynchronizationJobUnit(String name, IJobUnitFactory creator) {
        super(name, creator);
        securityService = PlatformContext.getPlatform().lookupService(ISecurityService.class);
        projectService = PlatformContext.getPlatform().lookupService(IProjectService.class);
        externalGraphConnector = OSGiUtils.lookupOSGiService(IGraphConnector.class);
    }

    @VisibleForTesting
    public UserSynchronizationJobUnit(String name, IJobUnitFactory creator, IOAuth2SecurityConfiguration authenticationProviderConfiguration, ISecurityService securityService, IProjectService projectService, IGraphConnector externalGraphConnector) {
        super(name, creator);
        this.authenticationProviderConfiguration = authenticationProviderConfiguration;
        this.securityService = securityService;
        this.projectService = projectService;
        this.externalGraphConnector = externalGraphConnector;
    }

    @Override
    protected IJobStatus runInternal(IProgressMonitor progress) {
        initializationCheck();
        JobLogger.getInstance().clear();
        JobLogger.getInstance().separator();
        JobLogger.getInstance().log(dryRun ?
                "|                    DRY RUN                    |" :
                "|                    REAL RUN                   |");
        JobLogger.getInstance().separator();

        JobLogger.getInstance().separator();
        IGraphService graphService = buildGraphService();
        JobLogger.getInstance().separator();

        JobLogger.getInstance().separator();
        final List<String> allMemberIds = new ArrayList<>(graphService.getAadMemberIds(groupPrefix));
        JobLogger.getInstance().separator();

        JobLogger.getInstance().separator();
        JobLogger.getInstance().log("Filtering members...");
        MemberFilter memberFilter = new MemberFilter(whitelist, blacklist);
        final List<String> filteredMemberIds = memberFilter.filterMembers(allMemberIds);
        JobLogger.getInstance().separator();

        if (checkLastSynchronization) {
            JobLogger.getInstance().separator();
            graphService.checkLastSynchronization();
            JobLogger.getInstance().separator();
        }

        JobLogger.getInstance().separator();
        IPolarionService polarionService = buildPolarionService(filteredMemberIds);
        JobLogger.getInstance().separator();

        JobLogger.getInstance().separator();
        JobLogger.getInstance().log("Checking for duplicated users in Polarion...");
        TransactionalExecutor.executeSafelyInReadOnlyTransaction(transaction -> {
                    polarionService.checkDuplicatedPolarionUsers();
                    return null;
                }
        );
        JobLogger.getInstance().separator();

        JobLogger.getInstance().separator();
        JobLogger.getInstance().log("Removing users in Polarion which are not in AzureAD anymore...");
        TransactionalExecutor.executeInWriteTransaction(transaction -> {
                    polarionService.deletePolarionUsers(filteredMemberIds);
                    return null;
                }
        );
        JobLogger.getInstance().separator();

        JobLogger.getInstance().separator();
        JobLogger.getInstance().log("Creating users in Polarion which have been added into AzureAD...");
        TransactionalExecutor.executeInWriteTransaction(transaction -> {
                    polarionService.createPolarionUsers(filteredMemberIds);
                    return null;
                }
        );
        JobLogger.getInstance().separator();

        return getStatusOK(JobLogger.getInstance().getLog());
    }

    @NotNull
    private IGraphService buildGraphService() {
        IGraphConnector graphConnector;
        if (externalGraphConnector != null) {
            graphConnector = externalGraphConnector;
            JobLogger.getInstance().log("Using external graph connector: " + externalGraphConnector.getClass());
        } else {
            String graphApiToken = new OAuth2Client().getToken(authenticationProviderConfiguration);
            graphConnector = new GraphConnector(authenticationProviderConfiguration, graphApiToken);
        }

        return new GraphService(graphConnector);
    }

    @NotNull
    private IPolarionService buildPolarionService(List<String> memberIds) {
        IPolarionServiceFactory externalServiceFactory = OSGiUtils.lookupOSGiService(IPolarionServiceFactory.class);
        IPolarionService polarionService;
        if (externalServiceFactory != null) {
            polarionService = externalServiceFactory.createPolarionService(securityService, projectService, dryRun, memberIds);
            JobLogger.getInstance().log("Using external polarion service: " + polarionService.getClass());
        } else {
            polarionService = new PolarionService(securityService, projectService, dryRun);
        }
        return polarionService;
    }

    private void initializationCheck() {
        if (isParameterNotProvided(authenticationProviderId)) {
            throw new NotFoundException("Authentication Provider ID should be provided via job properties");
        }
        this.authenticationProviderConfiguration = findAuthenticationProviderConfiguration(authenticationProviderId);

        if (isParameterNotProvided(groupPrefix)) {
            throw new NotFoundException("Group prefix should be provided via job properties");
        }
    }

    private IOAuth2SecurityConfiguration findAuthenticationProviderConfiguration(@NotNull String authenticationProviderId) {
        List<ILoginSecurityConfiguration> authenticators = AuthenticationManager.getInstance().authenticators();
        ILoginSecurityConfiguration loginSecurityConfiguration = authenticators.stream()
                .filter(authenticator -> authenticator.id().equals(authenticationProviderId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Authentication provider with ID '%s' can not be found".formatted(authenticationProviderId)));

        if (loginSecurityConfiguration instanceof IOAuth2SecurityConfiguration oAuth2SecurityConfiguration) {
            return oAuth2SecurityConfiguration;
        } else {
            throw new NotFoundException("Authentication provider with ID '%s' is not an OAuth2 provider".formatted(authenticationProviderId));
        }
    }


    private boolean isParameterNotProvided(String parameter) {
        return parameter == null || parameter.isBlank();
    }

    @Override
    public void setAuthenticationProviderId(String authenticationProviderId) {
        this.authenticationProviderId = authenticationProviderId;
    }

    @Override
    public void setGroupPrefix(String prefix) {
        this.groupPrefix = prefix;
    }

    @Override
    public void setWhitelist(Whitelist whitelist) {
        this.whitelist = whitelist;
    }

    @Override
    public void setBlacklist(Blacklist blacklist) {
        this.blacklist = blacklist;
    }

    @Override
    public void setDryRun(Boolean dryRun) {
        this.dryRun = dryRun;
    }

    @Override
    public void setCheckLastSynchronization(Boolean checkLastSynchronization) {
        this.checkLastSynchronization = checkLastSynchronization;
    }

}
