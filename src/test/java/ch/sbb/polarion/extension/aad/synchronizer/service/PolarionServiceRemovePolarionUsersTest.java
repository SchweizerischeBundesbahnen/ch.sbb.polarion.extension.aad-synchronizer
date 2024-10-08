package ch.sbb.polarion.extension.aad.synchronizer.service;

import ch.sbb.polarion.extension.aad.synchronizer.utils.CollectionUtils;
import ch.sbb.polarion.extension.generic.util.JobLogger;
import com.polarion.alm.projects.IProjectService;
import com.polarion.alm.projects.model.IProject;
import com.polarion.alm.projects.model.IProjectGroup;
import com.polarion.alm.projects.model.IUser;
import com.polarion.platform.persistence.spi.PObjectList;
import com.polarion.platform.security.ISecurityService;
import com.polarion.subterra.base.data.identification.IContextId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PolarionServiceRemovePolarionUsersTest {

    public static final String USER_1 = "user1";
    public static final String USER_1_UPPER_CASE = "USER1";
    public static final String USER_2 = "user2";
    public static final String USER_3 = "user3";
    public static final String ROLE_1 = "role1";
    public static final String ROLE_2 = "role2";
    public static final String ROLE_3 = "role3";
    public static final String PROJECT_1 = "project1";
    public static final String PROJECT_2 = "project2";
    public static final String testuser = "testuser";
    public static final String TESTUSER = "TESTUSER";

    @Mock
    private ISecurityService securityService;
    @Mock
    private IProjectService projectService;
    @Mock
    private IProjectGroup projectGroup;


    private static Stream<Arguments> testValues() {

        IProject mockedProject1 = mock(IProject.class);
        IContextId mockedProjectContextId1 = mock(IContextId.class);
        when(mockedProjectContextId1.getContextName()).thenReturn(PROJECT_1);
        when(mockedProject1.getContextId()).thenReturn(mockedProjectContextId1);

        List<IProject> projectList1 = List.of(mockedProject1);

        IContextId[] projectsContext1 = List.of(mockedProjectContextId1).toArray(new IContextId[0]);

        Map<IContextId, Collection<String>> projectRoles = Map.of(mockedProjectContextId1, List.of(ROLE_1));

        List<IUser> polarionUsers = UserTestUtils.getPolarionUsers(USER_1, USER_2, USER_3);

        Map<String, Map<IContextId, Collection<String>>> userProjectRoles1 = new HashMap<>();
        polarionUsers
                .forEach(user -> userProjectRoles1.put(user.getId(), projectRoles));

        IProject mockedProject2 = mock(IProject.class);
        IContextId mockedProjectContextId2 = mock(IContextId.class);
        when(mockedProjectContextId2.getContextName()).thenReturn(PROJECT_2);
        when(mockedProject2.getContextId()).thenReturn(mockedProjectContextId2);

        List<IProject> projectList2 = List.of(mockedProject1, mockedProject2);
        Map<IContextId, Collection<String>> allProjectRoles = new HashMap<>();
        allProjectRoles.put(mockedProjectContextId1, List.of(ROLE_1));
        allProjectRoles.put(mockedProjectContextId2, List.of(ROLE_2, ROLE_3));

        Map<IContextId, Collection<String>> firstProjectRoles = new HashMap<>();
        firstProjectRoles.put(mockedProjectContextId1, List.of(ROLE_1));

        Map<IContextId, Collection<String>> secondProjectRoles = new HashMap<>();
        secondProjectRoles.put(mockedProjectContextId2, List.of(ROLE_2, ROLE_3));

        IContextId[] projectsContext2 = List.of(mockedProjectContextId1, mockedProjectContextId2).toArray(new IContextId[0]);


        Map<String, Map<IContextId, Collection<String>>> contextRolesToUser2 = new HashMap<>();
        contextRolesToUser2.put(USER_1, allProjectRoles);
        contextRolesToUser2.put(USER_2, secondProjectRoles);
        contextRolesToUser2.put(USER_3, firstProjectRoles);

        List<String> expectedMessages1ForDryRun = List.of(
                polarionUsers.size() + " polarion user(s) have been found",
                "3 polarion user(s) can be deleted",
                CollectionUtils.usersAsString(List.of(USER_1, USER_2, USER_3)),
                "Processing user '" + USER_1 + "'...",
                "Role '" + ROLE_1 + "' can be deleted for user '" + USER_1 + "' in project '" + PROJECT_1 + "'",
                "User '" + USER_1 + "' can be deleted",
                "Processing user '" + USER_2 + "'...",
                "Role '" + ROLE_1 + "' can be deleted for user '" + USER_2 + "' in project '" + PROJECT_1 + "'",
                "User '" + USER_2 + "' can be deleted",
                "Processing user '" + USER_3 + "'...",
                "Role '" + ROLE_1 + "' can be deleted for user '" + USER_3 + "' in project '" + PROJECT_1 + "'",
                "User '" + USER_3 + "' can be deleted",
                "3 polarion user(s) could be deleted"
        );

        List<String> expectedMessages1ForRealRun = List.of(
                polarionUsers.size() + " polarion user(s) have been found",
                "3 polarion user(s) will be deleted",
                CollectionUtils.usersAsString(List.of(USER_1, USER_2, USER_3)),
                "Processing user '" + USER_1 + "'...",
                "Role '" + ROLE_1 + "' has been deleted for user '" + USER_1 + "' in project '" + PROJECT_1 + "'",
                "User '" + USER_1 + "' has been deleted",
                "Processing user '" + USER_2 + "'...",
                "Role '" + ROLE_1 + "' has been deleted for user '" + USER_2 + "' in project '" + PROJECT_1 + "'",
                "User '" + USER_2 + "' has been deleted",
                "Processing user '" + USER_3 + "'...",
                "Role '" + ROLE_1 + "' has been deleted for user '" + USER_3 + "' in project '" + PROJECT_1 + "'",
                "User '" + USER_3 + "' has been deleted",
                "3 polarion user(s) have been deleted"
        );

        List<String> expectedMessages2ForDryRun = List.of(
                polarionUsers.size() + " polarion user(s) have been found",
                "2 polarion user(s) can be deleted",
                CollectionUtils.usersAsString(List.of(USER_2, USER_3)),
                "Processing user '" + USER_2 + "'...",
                "Role '" + ROLE_1 + "' can be deleted for user '" + USER_2 + "' in project '" + PROJECT_1 + "'",
                "User '" + USER_2 + "' can be deleted",
                "Processing user '" + USER_3 + "'...",
                "Role '" + ROLE_1 + "' can be deleted for user '" + USER_3 + "' in project '" + PROJECT_1 + "'",
                "User '" + USER_3 + "' can be deleted",
                "2 polarion user(s) could be deleted"
        );

        List<String> expectedMessages2ForRealRun = List.of(
                polarionUsers.size() + " polarion user(s) have been found",
                "2 polarion user(s) will be deleted",
                CollectionUtils.usersAsString(List.of(USER_2, USER_3)),
                "Processing user '" + USER_2 + "'...",
                "Role '" + ROLE_1 + "' has been deleted for user '" + USER_2 + "' in project '" + PROJECT_1 + "'",
                "User '" + USER_2 + "' has been deleted",
                "Processing user '" + USER_3 + "'...",
                "Role '" + ROLE_1 + "' has been deleted for user '" + USER_3 + "' in project '" + PROJECT_1 + "'",
                "User '" + USER_3 + "' has been deleted",
                "2 polarion user(s) have been deleted"
        );

        List<Object> expectedMessages3ForDryRun = List.of(
                polarionUsers.size() + " polarion user(s) have been found",
                "0 polarion user(s) can be deleted",
                "0 polarion user(s) could be deleted"
        );

        List<String> expectedMessages4ForDryRun = List.of(
                polarionUsers.size() + " polarion user(s) have been found",
                "3 polarion user(s) can be deleted",
                CollectionUtils.usersAsString(List.of(USER_1, USER_2, USER_3)),
                "Processing user '" + USER_1 + "'...",
                "Role '" + ROLE_1 + "' can be deleted for user '" + USER_1 + "' in project '" + PROJECT_1 + "'",
                "Role '" + ROLE_2 + "' can be deleted for user '" + USER_1 + "' in project '" + PROJECT_2 + "'",
                "Role '" + ROLE_3 + "' can be deleted for user '" + USER_1 + "' in project '" + PROJECT_2 + "'",
                "User '" + USER_1 + "' can be deleted",
                "Processing user '" + USER_2 + "'...",
                "Role '" + ROLE_2 + "' can be deleted for user '" + USER_2 + "' in project '" + PROJECT_2 + "'",
                "Role '" + ROLE_3 + "' can be deleted for user '" + USER_2 + "' in project '" + PROJECT_2 + "'",
                "User '" + USER_2 + "' can be deleted",
                "Processing user '" + USER_3 + "'...",
                "Role '" + ROLE_1 + "' can be deleted for user '" + USER_3 + "' in project '" + PROJECT_1 + "'",
                "User '" + USER_3 + "' can be deleted",
                "3 polarion user(s) could be deleted"
        );

        List<String> expectedMessages4ForRealRun = List.of(
                polarionUsers.size() + " polarion user(s) have been found",
                "3 polarion user(s) will be deleted",
                CollectionUtils.usersAsString(List.of(USER_1, USER_2, USER_3)),
                "Processing user '" + USER_1 + "'...",
                "Role '" + ROLE_1 + "' has been deleted for user '" + USER_1 + "' in project '" + PROJECT_1 + "'",
                "Role '" + ROLE_2 + "' has been deleted for user '" + USER_1 + "' in project '" + PROJECT_2 + "'",
                "Role '" + ROLE_3 + "' has been deleted for user '" + USER_1 + "' in project '" + PROJECT_2 + "'",
                "User '" + USER_1 + "' has been deleted",
                "Processing user '" + USER_2 + "'...",
                "Role '" + ROLE_2 + "' has been deleted for user '" + USER_2 + "' in project '" + PROJECT_2 + "'",
                "Role '" + ROLE_3 + "' has been deleted for user '" + USER_2 + "' in project '" + PROJECT_2 + "'",
                "User '" + USER_2 + "' has been deleted",
                "Processing user '" + USER_3 + "'...",
                "Role '" + ROLE_1 + "' has been deleted for user '" + USER_3 + "' in project '" + PROJECT_1 + "'",
                "User '" + USER_3 + "' has been deleted",
                "3 polarion user(s) have been deleted"
        );

        List<Object> expectedMessages3ForRealRun = List.of(
                polarionUsers.size() + " polarion user(s) have been found",
                "0 polarion user(s) will be deleted",
                "0 polarion user(s) have been deleted"
        );

        List<String> expectedMessages5ForDryRun = List.of(
                polarionUsers.size() + " polarion user(s) have been found",
                "2 polarion user(s) can be deleted",
                CollectionUtils.usersAsString(List.of(USER_1, USER_3)),
                "Processing user '" + USER_1 + "'...",
                "Role '" + ROLE_1 + "' can be deleted for user '" + USER_1 + "' in project '" + PROJECT_1 + "'",
                "Role '" + ROLE_2 + "' can be deleted for user '" + USER_1 + "' in project '" + PROJECT_2 + "'",
                "Role '" + ROLE_3 + "' can be deleted for user '" + USER_1 + "' in project '" + PROJECT_2 + "'",
                "User '" + USER_1 + "' can be deleted",
                "Processing user '" + USER_3 + "'...",
                "Role '" + ROLE_1 + "' can be deleted for user '" + USER_3 + "' in project '" + PROJECT_1 + "'",
                "User '" + USER_3 + "' can be deleted",
                "2 polarion user(s) could be deleted"
        );

        List<String> expectedMessages5ForRealRun = List.of(
                polarionUsers.size() + " polarion user(s) have been found",
                "2 polarion user(s) will be deleted",
                CollectionUtils.usersAsString(List.of(USER_1, USER_3)),
                "Processing user '" + USER_1 + "'...",
                "Role '" + ROLE_1 + "' has been deleted for user '" + USER_1 + "' in project '" + PROJECT_1 + "'",
                "Role '" + ROLE_2 + "' has been deleted for user '" + USER_1 + "' in project '" + PROJECT_2 + "'",
                "Role '" + ROLE_3 + "' has been deleted for user '" + USER_1 + "' in project '" + PROJECT_2 + "'",
                "User '" + USER_1 + "' has been deleted",
                "Processing user '" + USER_3 + "'...",
                "Role '" + ROLE_1 + "' has been deleted for user '" + USER_3 + "' in project '" + PROJECT_1 + "'",
                "User '" + USER_3 + "' has been deleted",
                "2 polarion user(s) have been deleted"
        );

        return Stream.of(
                Arguments.of(polarionUsers, List.of(), expectedMessages1ForDryRun, projectList1, projectsContext1, userProjectRoles1, true),
                Arguments.of(polarionUsers, List.of(USER_1), expectedMessages2ForDryRun, projectList1, projectsContext1, userProjectRoles1, true),
                Arguments.of(polarionUsers, List.of(USER_1, USER_2, USER_3), expectedMessages3ForDryRun, projectList1, projectsContext1, userProjectRoles1, true),
                Arguments.of(polarionUsers, List.of(), expectedMessages4ForDryRun, projectList2, projectsContext2, contextRolesToUser2, true),
                Arguments.of(polarionUsers, List.of(USER_2), expectedMessages5ForDryRun, projectList2, projectsContext2, contextRolesToUser2, true),

                Arguments.of(polarionUsers, List.of(), expectedMessages1ForRealRun, projectList1, projectsContext1, userProjectRoles1, false),
                Arguments.of(polarionUsers, List.of(USER_1), expectedMessages2ForRealRun, projectList1, projectsContext1, userProjectRoles1, false),
                Arguments.of(polarionUsers, List.of(USER_1, USER_2, USER_3), expectedMessages3ForRealRun, projectList1, projectsContext1, userProjectRoles1, false),
                Arguments.of(polarionUsers, List.of(), expectedMessages4ForRealRun, projectList2, projectsContext2, contextRolesToUser2, false),
                Arguments.of(polarionUsers, List.of(USER_2), expectedMessages5ForRealRun, projectList2, projectsContext2, contextRolesToUser2, false)
        );
    }

    @ParameterizedTest
    @MethodSource("testValues")
    void testRemovePolarionUsers(
            List<IUser> polarionUsers,
            List<String> aadUsers,
            List<String> expectedMessages,
            List<IProject> projectList,
            IContextId[] projectsContext,
            Map<String, Map<IContextId, Collection<String>>> contextRolesToUser,
            boolean dryRun
    ) {
        when(projectGroup.getDeepContainedProjects()).thenReturn(new PObjectList(null, projectList));
        when(projectService.getUsers()).thenReturn(UserTestUtils.getProjectServiceUsers(polarionUsers));
        when(projectService.getRootProjectGroup()).thenReturn(projectGroup);
        JobLogger.getInstance().clear();

        polarionUsers
                .forEach(user -> {
                    final Map<IContextId, Collection<String>> roles = contextRolesToUser.get(user.getId());
                    Mockito.lenient().when(securityService.getContextRolesForUser(user.getId(), projectsContext)).thenReturn(roles);
                });

        PolarionService service = new PolarionService(securityService, projectService, dryRun);
        service.deletePolarionUsers(aadUsers);

        String lastLogMessage = expectedMessages.get(expectedMessages.size() - 1);
        int deletedUsers = UserTestUtils.getUsersFromLogMessage(lastLogMessage);

        assertThat(expectedMessages)
                .containsAll(JobLogger.getInstance().getMessages())
                .hasSize(JobLogger.getInstance().getMessages().size());
        verify(securityService, times(dryRun ? 0 : deletedUsers * projectsContext.length)).removeContextRoleFromUser(anyString(), anyString(), any());
    }

    @Test
    void testCaseInsensitiveDelete() {
        final List<String> aadUsers = List.of(USER_1, USER_2, USER_3);
        final List<IUser> polarionUsers = UserTestUtils.getPolarionUsers(USER_1_UPPER_CASE, USER_2, USER_3, testuser, TESTUSER);

        IProject mockedProject1 = mock(IProject.class);
        IContextId mockedProjectContextId1 = mock(IContextId.class);
        Mockito.lenient().when(mockedProjectContextId1.getContextName()).thenReturn(PROJECT_1);
        when(mockedProject1.getContextId()).thenReturn(mockedProjectContextId1);

        when(projectGroup.getDeepContainedProjects()).thenReturn(new PObjectList(null, List.of(mockedProject1)));
        when(projectService.getRootProjectGroup()).thenReturn(projectGroup);
        when(projectService.getUsers()).thenReturn(UserTestUtils.getProjectServiceUsers(polarionUsers));

        JobLogger.getInstance().clear();

        PolarionService service = new PolarionService(securityService, projectService, false);
        service.deletePolarionUsers(aadUsers);

        assertThat(JobLogger.getInstance().getMessages())
                .contains(
                        polarionUsers.size() + " polarion user(s) have been found",
                        "3 polarion user(s) will be deleted",
                        CollectionUtils.usersAsString(List.of(USER_1_UPPER_CASE, testuser, TESTUSER)),
                        "Processing user '" + USER_1_UPPER_CASE + "'...",
                        "User '" + USER_1_UPPER_CASE + "' has been found in AzureAD as '" + USER_1 + "'... SKIPPED!!!",
                        "Processing user '" + testuser + "'...",
                        "User '" + testuser + "' has been found in Polarion as '" + TESTUSER + "'... SKIPPED!!!",
                        "Processing user '" + TESTUSER + "'...",
                        "User '" + TESTUSER + "' has been found in Polarion as '" + testuser + "'... SKIPPED!!!",
                        "0 polarion user(s) have been deleted"

                );
        verify(securityService, times(0)).removeContextRoleFromUser(anyString(), anyString(), any());
    }

}
