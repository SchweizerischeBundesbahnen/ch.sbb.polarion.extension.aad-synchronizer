package ch.sbb.polarion.extension.aad.synchronizer;

import ch.sbb.polarion.extension.aad.synchronizer.connector.GraphConnector;
import ch.sbb.polarion.extension.aad.synchronizer.connector.IGraphConnector;
import ch.sbb.polarion.extension.aad.synchronizer.exception.NotFoundException;
import ch.sbb.polarion.extension.aad.synchronizer.service.GraphService;
import ch.sbb.polarion.extension.aad.synchronizer.service.IGraphService;
import ch.sbb.polarion.extension.aad.synchronizer.service.IPolarionService;
import ch.sbb.polarion.extension.aad.synchronizer.service.IPolarionServiceFactory;
import ch.sbb.polarion.extension.aad.synchronizer.service.PolarionService;
import ch.sbb.polarion.extension.aad.synchronizer.utils.OAuth2Client;
import ch.sbb.polarion.extension.generic.util.JobLogger;
import ch.sbb.polarion.extension.aad.synchronizer.utils.OSGiUtils;
import com.polarion.alm.projects.IProjectService;
import com.polarion.alm.shared.api.transaction.TransactionalExecutor;
import com.polarion.alm.shared.util.StringUtils;
import com.polarion.platform.core.PlatformContext;
import com.polarion.platform.internal.security.UserAccountVault;
import com.polarion.platform.jobs.IJobStatus;
import com.polarion.platform.jobs.IJobUnitFactory;
import com.polarion.platform.jobs.IProgressMonitor;
import com.polarion.platform.jobs.spi.AbstractJobUnit;
import com.polarion.platform.security.ISecurityService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;

public class UserSynchronizationJobUnit extends AbstractJobUnit implements AADUserSynchronizationJobUnit {
    private final ISecurityService securityService;
    private final IProjectService projectService;
    private final UserAccountVault vault;
    private final IGraphConnector externalGraphConnector;

    private static final String DEFAULT_GROUP_PREFIX = "DG_RBT_POLARION_TRACE_";
    private static final String DEFAULT_SCOPE = "https://graph.microsoft.com/.default";

    private String graphApiTokenUrl;
    private String graphApiClientId;
    private String graphApiClientSecret;
    private String graphApiClientSecretValue;
    private String graphApiScope;

    private String groupPrefix;
    private boolean dryRun = false;
    private boolean checkLastSynchronization = false;

    public UserSynchronizationJobUnit(String name, IJobUnitFactory creator) {
        super(name, creator);
        securityService = PlatformContext.getPlatform().lookupService(ISecurityService.class);
        projectService = PlatformContext.getPlatform().lookupService(IProjectService.class);
        vault = UserAccountVault.getInstance();
        externalGraphConnector = OSGiUtils.lookupOSGiService(IGraphConnector.class);
    }

    @VisibleForTesting
    public UserSynchronizationJobUnit(String name, IJobUnitFactory creator, ISecurityService securityService, IProjectService projectService, UserAccountVault vault, IGraphConnector externalGraphConnector) {
        super(name, creator);
        this.securityService = securityService;
        this.projectService = projectService;
        this.vault = vault;
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

        IGraphService graphService = buildGraphService();

        List<String> memberIds = new ArrayList<>(graphService.getAadMemberIds(groupPrefix));

        if (checkLastSynchronization) {
            graphService.checkLastSynchronization();
        }

        IPolarionService polarionService = buildPolarionService(memberIds);

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
                    polarionService.deletePolarionUsers(memberIds);
                    return null;
                }
        );
        JobLogger.getInstance().separator();

        JobLogger.getInstance().separator();
        JobLogger.getInstance().log("Creating users in Polarion which have been added into AzureAD...");
        TransactionalExecutor.executeInWriteTransaction(transaction -> {
                    polarionService.createPolarionUsers(memberIds);
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
            JobLogger.getInstance().separator();
            JobLogger.getInstance().log("Using external graph connector: " + externalGraphConnector.getClass());
            JobLogger.getInstance().separator();
        } else {
            String graphApiToken = new OAuth2Client().getToken(graphApiTokenUrl, graphApiClientId, graphApiClientSecretValue, graphApiScope);
            graphConnector = new GraphConnector(graphApiToken);
        }

        return new GraphService(graphConnector);
    }

    @NotNull
    private IPolarionService buildPolarionService(List<String> memberIds) {
        IPolarionServiceFactory externalServiceFactory = OSGiUtils.lookupOSGiService(IPolarionServiceFactory.class);
        IPolarionService polarionService;
        if (externalServiceFactory != null) {
            polarionService = externalServiceFactory.createPolarionService(securityService, projectService, dryRun, memberIds);
            JobLogger.getInstance().separator();
            JobLogger.getInstance().log("Using external polarion service: " + polarionService.getClass());
            JobLogger.getInstance().separator();
        } else {
            polarionService = new PolarionService(securityService, projectService, dryRun);
        }
        return polarionService;
    }

    private void initializationCheck() {
        if (isParameterNotProvided(graphApiTokenUrl)) {
            throw new NotFoundException("Token URL should be provided via job properties");
        }
        if (isParameterNotProvided(graphApiClientId)) {
            throw new NotFoundException("Client ID should be provided via job properties");
        }
        if (isParameterNotProvided(graphApiClientSecret)) {
            throw new NotFoundException("Client Secret should be provided via Polarion Vault");
        } else {
            graphApiClientSecretValue = getGraphApiClientSecretFromPolarionVault(graphApiClientSecret);
        }
        if (isParameterNotProvided(graphApiScope)) {
            this.graphApiScope = DEFAULT_SCOPE;
        }

        if (isParameterNotProvided(groupPrefix)) {
            this.groupPrefix = DEFAULT_GROUP_PREFIX;
        }
    }

    private boolean isParameterNotProvided(String parameter) {
        return parameter == null || parameter.isBlank();
    }

    @Override
    public void setGraphApiTokenUrl(String graphApiTokenUrl) {
        this.graphApiTokenUrl = graphApiTokenUrl;
    }

    @Override
    public void setGraphApiClientId(String graphApiClientId) {
        this.graphApiClientId = graphApiClientId;
    }

    @Override
    public void setGraphApiClientSecret(String graphApiClientSecret) {
        this.graphApiClientSecret = graphApiClientSecret;
    }

    @Override
    public void setGraphApiScope(String graphApiScope) {
        this.graphApiScope = graphApiScope;
    }

    @Override
    public void setGroupPrefix(String prefix) {
        this.groupPrefix = prefix;
    }

    @Override
    public void setDryRun(Boolean dryRun) {
        this.dryRun = dryRun;
    }

    @Override
    public void setCheckLastSynchronization(Boolean checkLastSynchronization) {
        this.checkLastSynchronization = checkLastSynchronization;
    }

    private String getGraphApiClientSecretFromPolarionVault(String graphApiClientSecretKey) {
        if (!StringUtils.isEmptyTrimmed(graphApiClientSecretKey)) {
            UserAccountVault.Credentials credentials = vault.getCredentialsForKey(graphApiClientSecretKey);
            return credentials.getPassword();
        }
        return null;
    }

}
