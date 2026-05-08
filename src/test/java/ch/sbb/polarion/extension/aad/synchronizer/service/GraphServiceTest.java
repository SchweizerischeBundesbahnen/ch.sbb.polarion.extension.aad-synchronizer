package ch.sbb.polarion.extension.aad.synchronizer.service;

import ch.sbb.polarion.extension.aad.synchronizer.connector.GraphConnector;
import ch.sbb.polarion.extension.aad.synchronizer.exception.NotFoundException;
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
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GraphServiceTest {

    @Mock
    private GraphConnector graphConnector;

    private static Stream<Arguments> testValues() {

        Map<String, List<Member>> map1 = new HashMap<>();
        map1.put("1", List.of(new Member("1", "name", "mail"),
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
    void prefixesOnly_dedupsMembersAcrossGroups(List<Group> groups, Map<String, List<Member>> membersInGroupMap, List<String> expected) {
        GraphService service = new GraphService(graphConnector);
        List<String> prefixes = List.of("SOME_");
        when(graphConnector.getGroups(prefixes)).thenReturn(groups);
        membersInGroupMap.forEach((groupId, members) ->
                when(graphConnector.getMembers(groupId)).thenReturn(members)
        );

        List<String> members = new ArrayList<>(service.getAadMemberIds(prefixes, List.of()));

        assertThat(members).hasSize(expected.size()).isEqualTo(expected);
    }

    @Test
    void patternsOnly_fetchesAllGroupsAndFiltersClientSide() {
        // No prefix → connector is called with an empty list (the "fetch all groups" path); the
        // regex then narrows server result client-side. Verifies both that the unfiltered fetch
        // is the wiring used and that anyMatch is the combinator across multiple patterns.
        Group alpha = new Group("g1", "ALPHA_TEAM");
        Group beta = new Group("g2", "BETA_TEAM");
        Group gamma = new Group("g3", "GAMMA_TEAM");
        when(graphConnector.getGroups(List.of())).thenReturn(List.of(alpha, beta, gamma));
        when(graphConnector.getMembers("g1")).thenReturn(List.of(new Member("ma", "n", "e")));
        when(graphConnector.getMembers("g2")).thenReturn(List.of(new Member("mb", "n", "e")));

        List<Pattern> patterns = List.of(Pattern.compile("^ALPHA_.*"), Pattern.compile("^BETA_.*"));
        GraphService service = new GraphService(graphConnector);

        List<String> members = new ArrayList<>(service.getAadMemberIds(List.of(), patterns));

        assertThat(members).containsExactlyInAnyOrder("ma", "mb");
    }

    @Test
    void patternsOnly_skipsGroupsWithoutDisplayName() {
        // Defensive: a Group without a displayName (e.g. an external IGraphConnector
        // implementation that doesn't populate it) must not blow up the regex match — it just
        // doesn't match anything.
        Group named = new Group("g1", "ALPHA_TEAM");
        Group unnamed = new Group("g2");
        when(graphConnector.getGroups(List.of())).thenReturn(List.of(named, unnamed));
        when(graphConnector.getMembers("g1")).thenReturn(List.of(new Member("ma", "n", "e")));

        Pattern pattern = Pattern.compile("^ALPHA_.*");
        GraphService service = new GraphService(graphConnector);

        List<String> members = new ArrayList<>(service.getAadMemberIds(List.of(), List.of(pattern)));

        assertThat(members).containsExactly("ma");
    }

    @Test
    void prefixesAndPatterns_unionsTwoIndependentFetches() {
        // Documents the union semantics introduced for the prefixes+patterns case: prefixes fetch
        // a server-filtered set and patterns fetch the full tenant + filter client-side; the
        // results are unioned by group id. Useful when an operator wants a broad family of groups
        // by prefix plus a handful of outliers captured only by a regex.
        Group prefixOnly = new Group("g-prefix", "TEAM_ALPHA");
        Group patternOnly = new Group("g-pattern", "ADMIN_SPECIAL");
        Group inBoth = new Group("g-both", "TEAM_BETA");

        // Prefix branch: server-side $filter on "TEAM_" returns the two TEAM_ groups.
        when(graphConnector.getGroups(List.of("TEAM_"))).thenReturn(List.of(prefixOnly, inBoth));
        // Pattern branch: unfiltered fetch returns the entire tenant; the regex below selects
        // ADMIN_SPECIAL plus TEAM_BETA. TEAM_BETA appears in both branches and must be deduped.
        when(graphConnector.getGroups(List.of())).thenReturn(List.of(prefixOnly, inBoth, patternOnly));
        when(graphConnector.getMembers("g-prefix")).thenReturn(List.of(new Member("m-prefix", "n", "e")));
        when(graphConnector.getMembers("g-pattern")).thenReturn(List.of(new Member("m-pattern", "n", "e")));
        when(graphConnector.getMembers("g-both")).thenReturn(List.of(new Member("m-both", "n", "e")));

        List<Pattern> patterns = List.of(Pattern.compile("^ADMIN_SPECIAL$"), Pattern.compile("^TEAM_BETA$"));
        GraphService service = new GraphService(graphConnector);

        List<String> members = new ArrayList<>(service.getAadMemberIds(List.of("TEAM_"), patterns));

        assertThat(members).containsExactlyInAnyOrder("m-prefix", "m-pattern", "m-both");
        // g-both is in both branches; getMembers must be invoked once, not twice. Verifies the
        // group dedup happens before member resolution rather than relying on the member set's
        // own dedup.
        verify(graphConnector).getMembers("g-both");
    }

    @Test
    void prefixesAndPatterns_recoverWhenPrefixesMatchNothing() {
        // An empty prefix result alone surfaces as NotFoundException from the connector. With
        // patterns also configured, the union may still be non-empty — so the prefix branch
        // failure is swallowed and the pattern branch carries the run.
        when(graphConnector.getGroups(List.of("MISSING_")))
                .thenThrow(new NotFoundException("No AAD groups were found."));
        when(graphConnector.getGroups(List.of())).thenReturn(List.of(new Group("g1", "PATTERN_HIT")));
        when(graphConnector.getMembers("g1")).thenReturn(List.of(new Member("m1", "n", "e")));

        List<Pattern> patterns = List.of(Pattern.compile("^PATTERN_HIT$"));
        GraphService service = new GraphService(graphConnector);

        List<String> members = new ArrayList<>(service.getAadMemberIds(List.of("MISSING_"), patterns));

        assertThat(members).containsExactly("m1");
    }

    @Test
    void prefixesOnly_propagatesNotFoundFromConnector() {
        // Without patterns the prefix branch is the only signal; an empty result must surface as
        // NotFoundException so the run fails loudly instead of wiping Polarion silently.
        when(graphConnector.getGroups(List.of("MISSING_")))
                .thenThrow(new NotFoundException("No AAD groups were found."));
        GraphService service = new GraphService(graphConnector);
        List<String> prefixes = List.of("MISSING_");
        List<Pattern> patterns = List.of();

        assertThatThrownBy(() -> service.getAadMemberIds(prefixes, patterns))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("No AAD groups were found");
        verify(graphConnector, never()).getGroups(List.of());
    }

    @Test
    void prefixesAndPatterns_throwWhenUnionIsEmpty() {
        // Both branches yield nothing → the union is empty and the run must fail loudly with a
        // message that lists both selectors as configured.
        when(graphConnector.getGroups(List.of("MISSING_")))
                .thenThrow(new NotFoundException("No AAD groups were found."));
        when(graphConnector.getGroups(List.of())).thenReturn(List.of(new Group("g1", "UNRELATED")));

        List<String> prefixes = List.of("MISSING_");
        List<Pattern> patterns = List.of(Pattern.compile("^WILL_NOT_MATCH$"));
        GraphService service = new GraphService(graphConnector);

        assertThatThrownBy(() -> service.getAadMemberIds(prefixes, patterns))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("groupPrefixes/groupPatterns");
    }

    @Test
    void patternsOnly_throwMessageMentionsOnlyConfiguredSelector() {
        // Patterns-only path: when the regex matches nothing, the failure message must reference
        // groupPatterns alone. Prior to this contract the unified message ("groupPrefixes/...")
        // misled operators who hadn't configured groupPrefixes at all.
        when(graphConnector.getGroups(List.of())).thenReturn(List.of(new Group("g1", "UNRELATED")));

        List<String> prefixes = List.of();
        List<Pattern> patterns = List.of(Pattern.compile("^WILL_NOT_MATCH$"));
        GraphService service = new GraphService(graphConnector);

        assertThatThrownBy(() -> service.getAadMemberIds(prefixes, patterns))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("groupPatterns")
                .hasMessageNotContaining("groupPrefixes");
    }

    @Test
    void checkLastSynchronization() {
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
