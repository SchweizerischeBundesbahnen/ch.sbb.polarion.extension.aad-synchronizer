package ch.sbb.polarion.extension.aad.synchronizer.service;

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
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PolarionServiceFindCaseInsensitiveDuplicatedPolarionUsersTest {

    public static final String USER_1_1 = "user1";
    public static final String USER_1_2 = "User1";
    public static final String USER_1_3 = "USER1";
    public static final String USER_2 = "user2";
    public static final String USER_3 = "user3";

    @Mock
    private ISecurityService securityService;
    @Mock
    private IProjectService projectService;


    private static Stream<Arguments> testValuesFindCaseInsensitiveDuplicatedPolarionUsers() {
        return Stream.of(
                Arguments.of(UserTestUtils.getPolarionUsers(USER_1_1, USER_2, USER_3, USER_1_3, USER_1_2, PolarionService.ADMIN),
                        Map.of(USER_1_1, List.of(USER_1_1, USER_1_2, USER_1_3)),
                        List.of(USER_1_1, USER_1_2, USER_1_3)),
                Arguments.of(UserTestUtils.getPolarionUsers(USER_1_1, USER_2, USER_3, PolarionService.ADMIN), Map.of(), List.of()),
                Arguments.of(UserTestUtils.getPolarionUsers(), Map.of(), List.of())
        );
    }

    @ParameterizedTest
    @MethodSource("testValuesFindCaseInsensitiveDuplicatedPolarionUsers")
    void testFindCaseInsensitiveDuplicatedPolarionUsers(
            List<IUser>  polarionUsers,
            Map<String, List<String>> expectedDuplicatedPolarionUsers,
            List<String> expectedDuplicatedUsers
    ) {
        when(projectService.getUsers()).thenReturn(UserTestUtils.getProjectServiceUsers(polarionUsers));
        JobLogger.getInstance().clear();

        PolarionService service = new PolarionService(securityService, projectService, true);
        final Map<String, List<String>> duplicatedPolarionUsers = service.findCaseInsensitiveDuplicatedPolarionUsers();

        assertEquals(expectedDuplicatedPolarionUsers.size(), duplicatedPolarionUsers.size());

        for (Map.Entry<String, List<String>> entry : expectedDuplicatedPolarionUsers.entrySet()) {
            final List<String> users = duplicatedPolarionUsers.get(entry.getKey());
            assertNotNull(users);
            assertThat(entry.getValue()).containsAll(users);
        }

        service.checkDuplicatedPolarionUsers();

        final List<String> logMessages = JobLogger.getInstance().getMessages();
        for (String logMessage : logMessages) {
            if (expectedDuplicatedUsers.isEmpty()) {
                assertThat(logMessage).contains("No duplicated users have been found");
            } else {
                assertThat(logMessage).contains("The following duplicated users have been found: ['").contains("']");
                for (String expectedDuplicatedUser : expectedDuplicatedUsers) {
                    assertThat(logMessage).contains(expectedDuplicatedUser);
                }
            }
        }
    }

}
