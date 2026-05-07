package ch.sbb.polarion.extension.aad.synchronizer.connector;

import ch.sbb.polarion.extension.aad.synchronizer.model.Group;
import ch.sbb.polarion.extension.aad.synchronizer.model.Member;
import ch.sbb.polarion.extension.aad.synchronizer.model.OrganizationData;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IGraphConnectorTest {

    @Test
    void defaultRequestCountIsZeroForImplementationsThatDoNotTrackRequests() {
        // External IGraphConnector implementations registered via OSGi are not required to
        // implement the request-count API. The interface provides a default of 0 which the job
        // logger treats as "unavailable" (suppresses the summary line).
        IGraphConnector connector = newSingularOnlyConnector(Map.of());

        assertThat(connector.getRequestCount()).isZero();
    }

    @Test
    void defaultGetGroupsListLoopsOverSingularAndDedupes() {
        // External IGraphConnector implementations may only override getGroups(String). The
        // default getGroups(List<String>) implementation must call getGroups(String) per prefix
        // and dedup by id so the same group appearing under two overlapping prefixes is
        // returned once. This exercises the OSGi backwards-compat path that the GraphConnector
        // override would normally bypass.
        Map<String, List<Group>> singular = new HashMap<>();
        singular.put("A_", List.of(new Group("g1"), new Group("shared")));
        singular.put("B_", List.of(new Group("g2"), new Group("shared")));
        IGraphConnector connector = newSingularOnlyConnector(singular);

        List<Group> result = connector.getGroups(List.of("A_", "B_"));

        assertThat(result).extracting(Group::getId)
                .as("default impl must dedup by group id when prefixes return overlapping sets")
                .containsExactly("g1", "shared", "g2");
    }

    @Test
    void defaultGetGroupsListDelegatesToSingularNullForEmptyList() {
        // Empty list and null list both mean "fetch all groups" in the contract. The default
        // impl must translate them to getGroups((String) null) so external implementations only
        // need to handle one shape.
        List<String> calls = new ArrayList<>();
        IGraphConnector connector = new IGraphConnector() {
            @Override
            public List<Group> getGroups(String groupPrefix) {
                calls.add(String.valueOf(groupPrefix));
                return List.of(new Group("all"));
            }

            @Override
            public List<Member> getMembers(String key) {
                return List.of();
            }

            @Override
            public OrganizationData getOrganizationData() {
                return null;
            }
        };

        assertThat(connector.getGroups(List.<String>of())).extracting(Group::getId).containsExactly("all");
        assertThat(connector.getGroups((List<String>) null)).extracting(Group::getId).containsExactly("all");
        assertThat(calls).containsExactly("null", "null");
    }

    private static IGraphConnector newSingularOnlyConnector(Map<String, List<Group>> singularReturns) {
        return new IGraphConnector() {
            @Override
            public List<Group> getGroups(String groupPrefix) {
                return singularReturns.getOrDefault(groupPrefix, List.of());
            }

            @Override
            public List<Member> getMembers(String key) {
                return List.of();
            }

            @Override
            public OrganizationData getOrganizationData() {
                return null;
            }
        };
    }
}
