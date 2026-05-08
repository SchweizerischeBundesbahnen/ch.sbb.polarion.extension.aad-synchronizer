package ch.sbb.polarion.extension.aad.synchronizer;

import ch.sbb.polarion.extension.aad.synchronizer.connector.FakeOAuth2SecurityConfiguration;
import ch.sbb.polarion.extension.aad.synchronizer.connector.GraphConnector;
import ch.sbb.polarion.extension.aad.synchronizer.connector.GraphFieldOverrides;
import ch.sbb.polarion.extension.aad.synchronizer.connector.IGraphConnector;
import ch.sbb.polarion.extension.aad.synchronizer.exception.NotFoundException;
import ch.sbb.polarion.extension.aad.synchronizer.model.Group;
import ch.sbb.polarion.extension.aad.synchronizer.model.GroupPatterns;
import ch.sbb.polarion.extension.aad.synchronizer.model.GroupPrefixes;
import ch.sbb.polarion.extension.aad.synchronizer.model.Member;
import ch.sbb.polarion.extension.aad.synchronizer.service.IPolarionServiceFactory;
import ch.sbb.polarion.extension.aad.synchronizer.service.PolarionService;
import ch.sbb.polarion.extension.aad.synchronizer.utils.OAuth2Client;
import ch.sbb.polarion.extension.aad.synchronizer.utils.OSGiUtils;
import com.polarion.alm.projects.IProjectService;
import com.polarion.alm.shared.api.transaction.ReadOnlyTransaction;
import com.polarion.alm.shared.api.transaction.RunnableInReadOnlyTransaction;
import com.polarion.alm.shared.api.transaction.RunnableInWriteTransaction;
import com.polarion.alm.shared.api.transaction.TransactionalExecutor;
import com.polarion.alm.shared.api.transaction.WriteTransaction;
import com.polarion.core.config.IOAuth2SecurityConfiguration;
import com.polarion.platform.jobs.IJob;
import com.polarion.platform.jobs.IJobStatus;
import com.polarion.platform.jobs.IJobUnitFactory;
import com.polarion.platform.jobs.IProgressMonitor;
import com.polarion.platform.security.ISecurityService;
import com.polarion.platform.security.auth.AuthenticationManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"deprecation", "removal"}) // exercises legacy <groupPrefix> path on purpose; drop with the deprecated method
class UserSynchronizationJobUnitTest {
    @Mock
    private IJobUnitFactory jobUnitFactory;
    private IOAuth2SecurityConfiguration authenticationProviderConfiguration = new FakeOAuth2SecurityConfiguration();
    @Mock
    private IProgressMonitor monitor;
    @Mock
    private ISecurityService securityService;
    @Mock
    private IProjectService projectService;
    @Mock
    private PolarionService polarionService;
    @Mock
    private IGraphConnector externalGraphConnector;

    private UserSynchronizationJobUnit userSynchronizationJobUnit;

    @BeforeEach
    void setup() {
        userSynchronizationJobUnit = new UserSynchronizationJobUnit("testName", jobUnitFactory, authenticationProviderConfiguration, securityService, projectService, externalGraphConnector);
        userSynchronizationJobUnit.setAuthenticationProviderId("authenticationProviderId");
        userSynchronizationJobUnit.setGroupPrefix("testPrefix");
        // Exercise the Graph field override setters so SonarQube counts them as covered. The
        // values are irrelevant for the externalGraphConnector path but the setter bodies must
        // execute at least once; the resolver itself is tested in GraphConnectorTest.
        userSynchronizationJobUnit.setGraphIdField("onPremisesSamAccountName");
        userSynchronizationJobUnit.setGraphNameField("displayName");
        userSynchronizationJobUnit.setGraphEmailField("mail");
        userSynchronizationJobUnit.setJob(mock(IJob.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldRunSuccessfully() {
        // Arrange
        try (MockedStatic<TransactionalExecutor> mockedExecutor = Mockito.mockStatic(TransactionalExecutor.class);
             MockedStatic<OSGiUtils> mockedOSGiUtils = Mockito.mockStatic(OSGiUtils.class);
             MockedStatic<AuthenticationManager> mockedAuthenticationManager = Mockito.mockStatic(AuthenticationManager.class, RETURNS_DEEP_STUBS)
        ) {
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
            mockedOSGiUtils.when(() -> OSGiUtils.lookupOSGiService(IPolarionServiceFactory.class)).thenReturn((IPolarionServiceFactory) (polarionSecurityService, polarionProjectService, dryRun, memberIds) -> polarionService);
            when(externalGraphConnector.getGroups(List.of("testPrefix"))).thenReturn(List.of(new Group("testGroupId")));
            when(externalGraphConnector.getMembers("testGroupId")).thenReturn(List.of(new Member("testNickName", "testDisplayName", "testEMail")));
            // Positive request count exercises the summary log branch in runWithGraphConnector.
            // The companion own-connector test leaves the count at Mockito's default 0 to cover
            // the complementary branch.
            when(externalGraphConnector.getRequestCount()).thenReturn(42);
            mockedAuthenticationManager.when(() -> AuthenticationManager.getInstance().authenticators()).thenReturn(List.of(authenticationProviderConfiguration));

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
    @SuppressWarnings("unchecked")
    void shouldRunSuccessfullyWithOwnGraphConnectorWhenExternalIsNotProvided() {
        // Exercises the ownGraphConnector branch of runInternal — the path taken in production
        // when no external IGraphConnector is registered in OSGi. OAuth2Client.getToken is
        // intercepted so no real HTTPS call happens, and GraphConnector construction is replaced
        // with a Mockito mock so the subsequent Graph calls come from the test.
        UserSynchronizationJobUnit jobUnit = new UserSynchronizationJobUnit(
                "testName", jobUnitFactory, authenticationProviderConfiguration,
                securityService, projectService, null);
        jobUnit.setAuthenticationProviderId("authenticationProviderId");
        jobUnit.setGroupPrefix("testPrefix");
        jobUnit.setGraphIdField("onPremisesSamAccountName");
        jobUnit.setJob(mock(IJob.class));

        try (MockedStatic<TransactionalExecutor> mockedExecutor = Mockito.mockStatic(TransactionalExecutor.class);
             MockedStatic<OSGiUtils> mockedOSGiUtils = Mockito.mockStatic(OSGiUtils.class);
             MockedStatic<AuthenticationManager> mockedAuthenticationManager = Mockito.mockStatic(AuthenticationManager.class, RETURNS_DEEP_STUBS);
             MockedConstruction<OAuth2Client> oauth2Mock = Mockito.mockConstruction(OAuth2Client.class, (mock, ctx) ->
                     when(mock.getToken(any(IOAuth2SecurityConfiguration.class))).thenReturn("fake-token"));
             MockedConstruction<GraphConnector> graphMock = Mockito.mockConstruction(GraphConnector.class, (mock, ctx) -> {
                 when(mock.getGroups(List.of("testPrefix"))).thenReturn(List.of(new Group("ownGroupId")));
                 when(mock.getMembers("ownGroupId")).thenReturn(List.of(new Member("ownNick", "ownDisplay", "own@example.com")));
             })
        ) {
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
            mockedOSGiUtils.when(() -> OSGiUtils.lookupOSGiService(IPolarionServiceFactory.class))
                    .thenReturn((IPolarionServiceFactory) (sec, prj, dry, ids) -> polarionService);
            mockedAuthenticationManager.when(() -> AuthenticationManager.getInstance().authenticators())
                    .thenReturn(List.of(authenticationProviderConfiguration));

            jobUnit.setVerboseGraphLog(true);

            IJobStatus jobStatus = jobUnit.runInternal(monitor);

            assertThat(jobStatus).isNotNull();
            assertThat(jobStatus.getType().getName()).isEqualTo("OK");
            // The ownGraphConnector branch must have actually constructed a GraphConnector —
            // otherwise the try-with-resources line would never execute in any test.
            assertThat(graphMock.constructed()).hasSize(1);
            // Pin the wiring of verboseGraphLog from the job parameter into the freshly built
            // connector. Without this verification a future refactor that drops the propagation
            // could silently regress the operator-facing toggle.
            verify(graphMock.constructed().get(0)).setVerboseLog(true);
            verify(polarionService).createPolarionUsers(List.of("ownNick"));
        }
    }

    @Test
    void buildGraphFieldOverridesReflectsSetters() {
        // Exercises the VisibleForTesting seam used by the production ownGraphConnector branch.
        // Covers the (otherwise untested) translation from the three String job parameters into
        // a GraphFieldOverrides value passed into GraphConnector.
        userSynchronizationJobUnit.setGraphIdField("onPremisesSamAccountName");
        userSynchronizationJobUnit.setGraphNameField("  displayName  ");
        userSynchronizationJobUnit.setGraphEmailField(null);

        GraphFieldOverrides overrides = userSynchronizationJobUnit.buildGraphFieldOverrides();

        assertThat(overrides.idField()).isEqualTo("onPremisesSamAccountName");
        assertThat(overrides.nameField()).isEqualTo("displayName");
        assertThat(overrides.emailField()).isNull();
    }

    @Test
    void shouldFailedByMissingAuthenticationProviderId() {
        // Arrange
        userSynchronizationJobUnit.setAuthenticationProviderId(null);

        // Act, Assert
        callRunInternalAndVerifyException("Authentication Provider ID");
    }

    @Test
    void shouldFailWhenNoGroupSelectorProvided() {
        // Validation contract: at least one of groupPrefix / groupPrefixes / groupPatterns must
        // be set, otherwise the job has no way to scope which AAD groups to read.
        userSynchronizationJobUnit.setGroupPrefix(null);
        userSynchronizationJobUnit.setGroupPrefixes(null);
        userSynchronizationJobUnit.setGroupPatterns(null);

        try (MockedStatic<AuthenticationManager> mockedAuthenticationManager = Mockito.mockStatic(AuthenticationManager.class, RETURNS_DEEP_STUBS)) {
            mockedAuthenticationManager.when(() -> AuthenticationManager.getInstance().authenticators())
                    .thenReturn(List.of(authenticationProviderConfiguration));

            callRunInternalAndVerifyException("groupPrefix");
        }
    }

    @Test
    void shouldFailWhenLegacyAndPluralPrefixesAreBothSet() {
        // Mutual exclusion: if a deployer is migrating to <groupPrefixes>, accidentally leaving
        // the legacy <groupPrefix> in place must surface as a configuration error rather than
        // silently picking one of the two.
        userSynchronizationJobUnit.setGroupPrefix("legacyPrefix");
        userSynchronizationJobUnit.setGroupPrefixes(new GroupPrefixes(List.of("A_", "B_")));

        try (MockedStatic<AuthenticationManager> mockedAuthenticationManager = Mockito.mockStatic(AuthenticationManager.class, RETURNS_DEEP_STUBS)) {
            mockedAuthenticationManager.when(() -> AuthenticationManager.getInstance().authenticators())
                    .thenReturn(List.of(authenticationProviderConfiguration));

            callRunInternalAndVerifyException("both <groupPrefix> and <groupPrefixes>");
        }
    }

    @Test
    void shouldFailOnInvalidGroupPattern() {
        // An invalid regex should fail the job at start with a clear configuration error rather
        // than throwing PatternSyntaxException deep inside the run.
        userSynchronizationJobUnit.setGroupPrefix(null);
        userSynchronizationJobUnit.setGroupPatterns(new GroupPatterns(List.of("[invalid(")));

        try (MockedStatic<AuthenticationManager> mockedAuthenticationManager = Mockito.mockStatic(AuthenticationManager.class, RETURNS_DEEP_STUBS)) {
            mockedAuthenticationManager.when(() -> AuthenticationManager.getInstance().authenticators())
                    .thenReturn(List.of(authenticationProviderConfiguration));

            callRunInternalAndVerifyException("not a valid regular expression");
        }
    }

    @Test
    void shouldFailWhenTooManyGroupPrefixes() {
        // The 15-clause OR limit for Microsoft Graph $filter is enforced at job init so the
        // operator gets a clear error with the offending count, instead of an opaque HTTP 400 in
        // the middle of the run.
        List<String> tooMany = new java.util.ArrayList<>();
        for (int i = 0; i < 16; i++) {
            tooMany.add("PREFIX_" + i + "_");
        }
        userSynchronizationJobUnit.setGroupPrefix(null);
        userSynchronizationJobUnit.setGroupPrefixes(new GroupPrefixes(tooMany));

        try (MockedStatic<AuthenticationManager> mockedAuthenticationManager = Mockito.mockStatic(AuthenticationManager.class, RETURNS_DEEP_STUBS)) {
            mockedAuthenticationManager.when(() -> AuthenticationManager.getInstance().authenticators())
                    .thenReturn(List.of(authenticationProviderConfiguration));

            callRunInternalAndVerifyException("Too many groupPrefixes");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldRunWithGroupPatternsOnly() {
        // Pattern-only configuration: no server-side prefix, regex applied client-side. Verifies
        // the pattern actually filters groups before resolving members and that only patterns
        // (no prefixes) is a valid configuration.
        userSynchronizationJobUnit.setGroupPrefix(null);
        userSynchronizationJobUnit.setGroupPatterns(new GroupPatterns(List.of("^SOME(_OTHER)?_GROUP_PREFIX_.*")));

        try (MockedStatic<TransactionalExecutor> mockedExecutor = Mockito.mockStatic(TransactionalExecutor.class);
             MockedStatic<OSGiUtils> mockedOSGiUtils = Mockito.mockStatic(OSGiUtils.class);
             MockedStatic<AuthenticationManager> mockedAuthenticationManager = Mockito.mockStatic(AuthenticationManager.class, RETURNS_DEEP_STUBS)
        ) {
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
            mockedOSGiUtils.when(() -> OSGiUtils.lookupOSGiService(IPolarionServiceFactory.class))
                    .thenReturn((IPolarionServiceFactory) (s, p, d, ids) -> polarionService);
            // No prefix → connector called with empty list. Three groups: two should pass the
            // regex, one (SOME_IGNORED_…) must be filtered out.
            when(externalGraphConnector.getGroups(List.<String>of())).thenReturn(List.of(
                    new Group("kept1", "SOME_GROUP_PREFIX_X"),
                    new Group("kept2", "SOME_OTHER_GROUP_PREFIX_Y"),
                    new Group("dropped", "SOME_IGNORED_GROUP_PREFIX_Z")
            ));
            when(externalGraphConnector.getMembers("kept1")).thenReturn(List.of(new Member("u1", "n", "e")));
            when(externalGraphConnector.getMembers("kept2")).thenReturn(List.of(new Member("u2", "n", "e")));
            mockedAuthenticationManager.when(() -> AuthenticationManager.getInstance().authenticators())
                    .thenReturn(List.of(authenticationProviderConfiguration));

            IJobStatus jobStatus = userSynchronizationJobUnit.runInternal(monitor);

            assertThat(jobStatus.getType().getName()).isEqualTo("OK");
            verify(externalGraphConnector, never()).getMembers("dropped");
            verify(polarionService).createPolarionUsers(argThat(ids -> ids.size() == 2 && ids.contains("u1") && ids.contains("u2")));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldRunWithMultipleGroupPrefixes() {
        // Plural <groupPrefixes>: connector receives the full list and OR-combines server-side.
        // The legacy singular setter must NOT be set in the same job (mutual exclusion is covered
        // by a dedicated test above), so reset it here.
        userSynchronizationJobUnit.setGroupPrefix(null);
        userSynchronizationJobUnit.setGroupPrefixes(new GroupPrefixes(List.of("LEGACY_", "NEW_")));

        try (MockedStatic<TransactionalExecutor> mockedExecutor = Mockito.mockStatic(TransactionalExecutor.class);
             MockedStatic<OSGiUtils> mockedOSGiUtils = Mockito.mockStatic(OSGiUtils.class);
             MockedStatic<AuthenticationManager> mockedAuthenticationManager = Mockito.mockStatic(AuthenticationManager.class, RETURNS_DEEP_STUBS)
        ) {
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
            mockedOSGiUtils.when(() -> OSGiUtils.lookupOSGiService(IPolarionServiceFactory.class))
                    .thenReturn((IPolarionServiceFactory) (s, p, d, ids) -> polarionService);
            when(externalGraphConnector.getGroups(List.of("LEGACY_", "NEW_"))).thenReturn(List.of(
                    new Group("g1", "LEGACY_TEAM"),
                    new Group("g2", "NEW_TEAM")
            ));
            when(externalGraphConnector.getMembers("g1")).thenReturn(List.of(new Member("u1", "n", "e")));
            when(externalGraphConnector.getMembers("g2")).thenReturn(List.of(new Member("u2", "n", "e")));
            mockedAuthenticationManager.when(() -> AuthenticationManager.getInstance().authenticators())
                    .thenReturn(List.of(authenticationProviderConfiguration));

            IJobStatus jobStatus = userSynchronizationJobUnit.runInternal(monitor);

            assertThat(jobStatus.getType().getName()).isEqualTo("OK");
            verify(polarionService).createPolarionUsers(argThat(ids -> ids.size() == 2 && ids.contains("u1") && ids.contains("u2")));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldDropBlankAndNullEntriesInPluralPrefixesAndPatterns() {
        // Defensive cleanup at job init: blank/null <groupPrefix/> children produce a broken
        // OData filter (startswith(displayName, '') matches every group), and blank/null
        // <groupPattern/> children would compile to an empty regex and match every name. Both
        // must be silently dropped before reaching Graph.
        java.util.ArrayList<String> prefixesWithBlanks = new java.util.ArrayList<>();
        prefixesWithBlanks.add("KEEP_");
        prefixesWithBlanks.add(null);
        prefixesWithBlanks.add("");
        prefixesWithBlanks.add("   ");
        java.util.ArrayList<String> patternsWithBlanks = new java.util.ArrayList<>();
        patternsWithBlanks.add("^KEEP_.*");
        patternsWithBlanks.add(null);
        patternsWithBlanks.add("");
        patternsWithBlanks.add("   ");

        userSynchronizationJobUnit.setGroupPrefix(null);
        userSynchronizationJobUnit.setGroupPrefixes(new GroupPrefixes(prefixesWithBlanks));
        userSynchronizationJobUnit.setGroupPatterns(new GroupPatterns(patternsWithBlanks));

        try (MockedStatic<TransactionalExecutor> mockedExecutor = Mockito.mockStatic(TransactionalExecutor.class);
             MockedStatic<OSGiUtils> mockedOSGiUtils = Mockito.mockStatic(OSGiUtils.class);
             MockedStatic<AuthenticationManager> mockedAuthenticationManager = Mockito.mockStatic(AuthenticationManager.class, RETURNS_DEEP_STUBS)
        ) {
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
            mockedOSGiUtils.when(() -> OSGiUtils.lookupOSGiService(IPolarionServiceFactory.class))
                    .thenReturn((IPolarionServiceFactory) (s, p, d, ids) -> polarionService);
            // Both selectors are configured, so under the union semantics the connector receives
            // two calls: getGroups(List.of("KEEP_")) for the prefix branch (server-side $filter)
            // and getGroups(List.of()) for the pattern branch (unfiltered tenant fetch). Both
            // arguments must match the cleaned-up list — if cleanup is broken either stub won't
            // match and the run fails with NotFoundException from the connector.
            when(externalGraphConnector.getGroups(List.of("KEEP_"))).thenReturn(List.of(
                    new Group("g1", "KEEP_GROUP")
            ));
            when(externalGraphConnector.getGroups(List.of())).thenReturn(List.of(
                    new Group("g1", "KEEP_GROUP")
            ));
            when(externalGraphConnector.getMembers("g1")).thenReturn(List.of(new Member("u1", "n", "e")));
            mockedAuthenticationManager.when(() -> AuthenticationManager.getInstance().authenticators())
                    .thenReturn(List.of(authenticationProviderConfiguration));

            IJobStatus jobStatus = userSynchronizationJobUnit.runInternal(monitor);

            assertThat(jobStatus.getType().getName()).isEqualTo("OK");
            verify(polarionService).createPolarionUsers(List.of("u1"));
        }
    }

    private void callRunInternalAndVerifyException(String message) {
        assertThatThrownBy(() -> userSynchronizationJobUnit.runInternal(monitor))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(message);
    }
}
