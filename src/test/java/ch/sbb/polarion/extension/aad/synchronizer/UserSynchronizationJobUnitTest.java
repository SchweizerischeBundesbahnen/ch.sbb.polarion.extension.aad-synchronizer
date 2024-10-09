package ch.sbb.polarion.extension.aad.synchronizer;

import ch.sbb.polarion.extension.aad.synchronizer.connector.IGraphConnector;
import ch.sbb.polarion.extension.aad.synchronizer.exception.NotFoundException;
import ch.sbb.polarion.extension.aad.synchronizer.model.Group;
import ch.sbb.polarion.extension.aad.synchronizer.model.Member;
import ch.sbb.polarion.extension.aad.synchronizer.service.IPolarionServiceFactory;
import ch.sbb.polarion.extension.aad.synchronizer.service.PolarionService;
import ch.sbb.polarion.extension.aad.synchronizer.utils.OSGiUtils;
import com.polarion.alm.projects.IProjectService;
import com.polarion.alm.shared.api.transaction.ReadOnlyTransaction;
import com.polarion.alm.shared.api.transaction.RunnableInReadOnlyTransaction;
import com.polarion.alm.shared.api.transaction.RunnableInWriteTransaction;
import com.polarion.alm.shared.api.transaction.TransactionalExecutor;
import com.polarion.alm.shared.api.transaction.WriteTransaction;
import com.polarion.platform.internal.security.UserAccountVault;
import com.polarion.platform.jobs.IJob;
import com.polarion.platform.jobs.IJobStatus;
import com.polarion.platform.jobs.IJobUnitFactory;
import com.polarion.platform.jobs.IProgressMonitor;
import com.polarion.platform.security.ISecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserSynchronizationJobUnitTest {
    @Mock
    private IJobUnitFactory jobUnitFactory;
    @Mock
    private IProgressMonitor monitor;
    @Mock
    private ISecurityService securityService;
    @Mock
    private IProjectService projectService;
    @Mock
    private PolarionService polarionService;
    @Mock
    private UserAccountVault vault;
    @Mock
    private IGraphConnector externalGraphConnector;

    private UserSynchronizationJobUnit userSynchronizationJobUnit;

    @BeforeEach
    public void setup() {
        userSynchronizationJobUnit = new UserSynchronizationJobUnit("testName", jobUnitFactory, securityService, projectService, vault, externalGraphConnector);
        userSynchronizationJobUnit.setGraphApiClientId("testApiClientId");
        userSynchronizationJobUnit.setGraphApiTokenUrl("testTokenUrl");
        userSynchronizationJobUnit.setGraphApiClientSecret("testClientSecret");
        userSynchronizationJobUnit.setGroupPrefix("testPrefix");
        userSynchronizationJobUnit.setJob(mock(IJob.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldRunSuccessfully() {
        // Arrange
        try (MockedStatic<TransactionalExecutor> mockedExecutor = Mockito.mockStatic(TransactionalExecutor.class);
             MockedStatic<OSGiUtils> mockedOSGiUtils = Mockito.mockStatic(OSGiUtils.class)) {
            mockedExecutor.when(() -> TransactionalExecutor.executeSafelyInReadOnlyTransaction(any(RunnableInReadOnlyTransaction.class)))
                    .thenAnswer(invocation -> {
                        RunnableInReadOnlyTransaction<?> transaction = invocation.getArgument(0);
                        return transaction.run(mock(ReadOnlyTransaction.class));
                    });
            mockedExecutor.when(() -> TransactionalExecutor.executeInWriteTransaction(any(RunnableInWriteTransaction.class)))
                    .thenAnswer(invocation -> {
                        RunnableInWriteTransaction<?> transaction = invocation.getArgument(0);
                        return transaction.run(mock(WriteTransaction.class));
                    });
            mockedOSGiUtils.when(() -> OSGiUtils.lookupOSGiService(IPolarionServiceFactory.class)).thenReturn((IPolarionServiceFactory) (securityService, projectService, dryRun, memberIds) -> polarionService);
            when(vault.getCredentialsForKey("testClientSecret")).thenReturn(mock(UserAccountVault.Credentials.class));
            when(externalGraphConnector.getGroups("testPrefix")).thenReturn(List.of(new Group("testGroupId")));
            when(externalGraphConnector.getMembers("testGroupId")).thenReturn(List.of(new Member("testDisplayName", "testEMail", "testNickName")));

            // Act
            IJobStatus jobStatus = userSynchronizationJobUnit.runInternal(monitor);

            // Assert
            assertThat(jobStatus).isNotNull();
            assertThat(jobStatus.getType().getName()).isEqualTo("OK");
            verify(polarionService).checkDuplicatedPolarionUsers();
            verify(polarionService).deletePolarionUsers(List.of("testNickName"));
            verify(polarionService).createPolarionUsers(List.of("testNickName"));
        }
    }

    @Test
    void shouldFailedByMissingGraphApiTokenUrl() {
        // Arrange
        userSynchronizationJobUnit.setGraphApiTokenUrl(null);

        // Act, Assert
        callRunInternalAndVerifyException("Token URL");
    }

    @Test
    void shouldFailedByMissingGraphApiClientId() {
        // Arrange
        userSynchronizationJobUnit.setGraphApiClientId(null);

        // Act, Assert
        callRunInternalAndVerifyException("Client ID");
    }

    @Test
    void shouldFailedByMissingGraphApiClientSecret() {
        // Arrange
        userSynchronizationJobUnit.setGraphApiClientSecret(null);

        // Act, Assert
        callRunInternalAndVerifyException("Client Secret");
    }

    private void callRunInternalAndVerifyException(String message) {
        assertThatThrownBy(() -> userSynchronizationJobUnit.runInternal(monitor))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(message);
    }
}