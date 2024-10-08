package ch.sbb.polarion.extension.aad.synchronizer.service;

import ch.sbb.polarion.extension.aad.synchronizer.utils.CollectionUtils;
import ch.sbb.polarion.extension.generic.util.JobLogger;
import com.polarion.alm.projects.IProjectService;
import com.polarion.alm.projects.model.IUser;
import com.polarion.platform.security.ISecurityService;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PolarionServiceCreatePolarionUsersTest {

    public static final String USER_1 = "user1";
    public static final String USER_1_UPPER_CASE = "USER1";
    public static final String USER_2 = "user2";
    public static final String USER_3 = "user3";
    public static final String USER_4 = "user4";
    public static final String USER_5 = "user5";

    @Mock
    private ISecurityService securityService;
    @Mock
    private IProjectService projectService;

    private static Stream<Arguments> testValues() {
        List<IUser> polarionUsers = UserTestUtils.getPolarionUsers(USER_1, USER_2, USER_3);
        List<IUser> polarionUpperCaseUsers = UserTestUtils.getPolarionUsers(USER_1_UPPER_CASE, USER_2, USER_3);

        List<String> expectedMessagesNoUsersForCreationFound = List.of(
                polarionUsers.size() + " polarion user(s) have been found",
                "0 polarion user(s) will be created",
                "0 polarion user(s) could be created"
        );

        List<String> expectedMessagesForDryRun = List.of(
                polarionUsers.size() + " polarion user(s) have been found",
                "2 polarion user(s) will be created",
                "User '" + USER_4 + "' can be created",
                "User '" + USER_5 + "' can be created",
                CollectionUtils.usersAsString(List.of(USER_4, USER_5)),
                "2 polarion user(s) could be created"
        );

        List<String> expectedMessagesForRealRun = List.of(
                polarionUsers.size() + " polarion user(s) have been found",
                "2 polarion user(s) will be created",
                "User '" + USER_4 + "' has been created",
                "User '" + USER_5 + "' has been created",
                CollectionUtils.usersAsString(List.of(USER_4, USER_5)),
                "2 polarion user(s) have been created"
        );

        List<String> expectedMessagesDuplicatedUsers = List.of(
                polarionUsers.size() + " polarion user(s) have been found",
                "1 polarion user(s) will be created",
                CollectionUtils.usersAsString(List.of(USER_1)),
                "User '" + USER_1 + "' has been found as '" + USER_1_UPPER_CASE + "'... SKIPPED!!!",
                "0 polarion user(s) could be created"
        );

        return Stream.of(
                Arguments.of(polarionUsers, List.of(), expectedMessagesNoUsersForCreationFound, true),
                Arguments.of(polarionUsers, List.of(USER_1), expectedMessagesNoUsersForCreationFound, true),
                Arguments.of(polarionUsers, List.of(USER_1, USER_2, USER_3), expectedMessagesNoUsersForCreationFound, true),
                Arguments.of(polarionUsers, List.of(USER_1, USER_2, USER_3, USER_4, USER_5), expectedMessagesForDryRun, true),
                Arguments.of(polarionUsers, List.of(USER_1, USER_2, USER_3, USER_4, USER_5), expectedMessagesForRealRun, false),
                Arguments.of(polarionUpperCaseUsers, List.of(USER_1, USER_2, USER_3), expectedMessagesDuplicatedUsers, true)
        );
    }

    @ParameterizedTest
    @MethodSource("testValues")
    void testCreatePolarionUsers(
            List<IUser> polarionUsers,
            List<String> aadUsers,
            List<String> expectedMessages,
            boolean dryRun
    ) {
        when(projectService.getUsers()).thenReturn(UserTestUtils.getProjectServiceUsers(polarionUsers));
        JobLogger.getInstance().clear();

        PolarionService service = new PolarionService(securityService, projectService, dryRun);
        service.createPolarionUsers(aadUsers);

        String lastLogMessage = expectedMessages.get(expectedMessages.size() - 1);
        int createdUsers = UserTestUtils.getUsersFromLogMessage(lastLogMessage);

        verify(projectService, times(dryRun ? 0 : createdUsers)).createUser(anyString());
        assertThat(expectedMessages)
                .containsAll(JobLogger.getInstance().getMessages())
                .hasSize(JobLogger.getInstance().getMessages().size());
    }

}
