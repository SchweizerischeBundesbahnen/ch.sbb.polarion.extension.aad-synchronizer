package ch.sbb.polarion.extension.aad.synchronizer.service;

import ch.sbb.polarion.extension.aad.synchronizer.connector.GraphConnector;
import ch.sbb.polarion.extension.aad.synchronizer.model.Group;
import ch.sbb.polarion.extension.aad.synchronizer.model.Member;
import ch.sbb.polarion.extension.aad.synchronizer.model.OrganizationData;
import ch.sbb.polarion.extension.aad.synchronizer.utils.TimeUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GraphServiceTest {

    @Mock
    private GraphConnector graphConnector;

    private static Stream<Arguments> testValues() {

        Map<String, List<Member>> map1 = new HashMap<>();
        map1.put("1", List.of(new Member("1","name", "mail"),
                new Member("2", "name", "mail")));
        map1.put("2", List.of(new Member("3", "name", "mail"),
                new Member("4", "name", "mail")));
        map1.put("3", List.of(new Member("5", "name", "mail"),
                new Member("6", "name", "mail"), new Member("7", "name", "mail")));

        Map<String, List<Member>> map2 = new HashMap<>();
        map2.put("1", List.of(new Member("1", "name", "mail"),
                new Member("2", "name", "mail")));
        map2.put("2", List.of(new Member("1", "name", "mail"),
                new Member("2", "name", "mail")));
        map2.put("3", List.of(new Member("1", "name", "mail"),
                new Member("2", "name", "mail"), new Member("7", "name", "mail")));

        return Stream.of(
                Arguments.of(List.of(new Group("1"), new Group("2"), new Group("3")), map1, List.of("1", "2", "3", "4", "5", "6", "7")),
                Arguments.of(List.of(new Group("1"), new Group("2"), new Group("3")), map2, List.of("1", "2", "7")),
                Arguments.of(List.of(new Group("1"), new Group("2"), new Group("3")), Map.of(), List.of())
        );
    }

    @ParameterizedTest
    @MethodSource("testValues")
    void testGetAadMemberIds(List<Group> groups, Map<String, List<Member>> membersInGroupMap, List<String> expected) {

        GraphService service = new GraphService(graphConnector);
        String groupPrefix = anyString();
        when(graphConnector.getGroups(groupPrefix)).thenReturn(groups);
        membersInGroupMap.forEach((groupId, members) ->
                when(graphConnector.getMembers(groupId)).thenReturn(members)
        );

        List<String> members = new ArrayList<>(service.getAadMemberIds(groupPrefix));

        assertThat(members).hasSize(expected.size()).isEqualTo(expected);
    }

    @Test
    void testCheckLastSynchronization() {
        Date date = new Date();
        GraphService service = new GraphService(graphConnector);
        OrganizationData data = new OrganizationData(date);

        when(graphConnector.getOrganizationData()).thenReturn(data);

        try (MockedStatic<TimeUtils> mockStatic = mockStatic(TimeUtils.class)) {
            service.checkLastSynchronization();
            mockStatic.verify(() -> TimeUtils.isExpiredAADSync(date));
        }
    }
}
