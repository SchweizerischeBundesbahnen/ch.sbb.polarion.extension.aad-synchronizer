package ch.sbb.polarion.extension.aad.synchronizer.connector;

import ch.sbb.polarion.extension.aad.synchronizer.exception.InvalidGraphResponseException;
import ch.sbb.polarion.extension.aad.synchronizer.exception.NotFoundException;
import ch.sbb.polarion.extension.aad.synchronizer.exception.ResponseParsingException;
import ch.sbb.polarion.extension.aad.synchronizer.model.Group;
import ch.sbb.polarion.extension.aad.synchronizer.model.Member;
import ch.sbb.polarion.extension.aad.synchronizer.model.OrganizationData;
import ch.sbb.polarion.extension.aad.synchronizer.model.OrganizationDataWrapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.polarion.core.config.IOAuth2SecurityConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@WireMockTest
class GraphConnectorTest {

    private final IOAuth2SecurityConfiguration authenticationProviderConfiguration = new FakeOAuth2SecurityConfiguration();
    private final String groupPrefix = "test";
    /**
     * Tracks every {@link GraphConnector} created during a test so {@link #closeConnectors()} can
     * release the underlying JAX-RS HTTP client. Each connector now allocates a real
     * {@code Client} in its constructor (see {@code GraphConnector#httpClient}); leaving them open
     * across tests would leak Jersey connection pools and trigger noisy finalizer warnings.
     */
    private final List<GraphConnector> connectorsToClose = new ArrayList<>();

    @AfterEach
    void closeConnectors() {
        for (GraphConnector connector : connectorsToClose) {
            try {
                connector.close();
            } catch (RuntimeException ignored) {
                // best-effort cleanup; the test result is what matters
            }
        }
        connectorsToClose.clear();
    }

    private GraphConnector register(GraphConnector connector) {
        connectorsToClose.add(connector);
        return connector;
    }

    private GraphConnector createConnector(WireMockRuntimeInfo wmRuntimeInfo) {
        return register(new GraphConnector(authenticationProviderConfiguration, "test", null, wmRuntimeInfo.getHttpBaseUrl()));
    }

    private static Stream<Arguments> testMemberValues() {
        return Stream.of(
                Arguments.of("groupMemberIds.json", 5),
                Arguments.of("emptyGroupMemberIds.json", 0)
        );
    }

    private static Stream<Arguments> testGroupValues() {
        return Stream.of(
                Arguments.of(200, NotFoundException.class, "No AAD groups were found"),
                Arguments.of(222, InvalidGraphResponseException.class, "Could not get proper response from Microsoft Graph"),
                Arguments.of(401, InvalidGraphResponseException.class, "Microsoft Graph token expired")
        );
    }

    private static Stream<Arguments> testOrganizationValues() {
        return Stream.of(
                Arguments.of(200, NotFoundException.class, "No Organization data was found"),
                Arguments.of(222, InvalidGraphResponseException.class, "Could not get proper response from Microsoft Graph"),
                Arguments.of(401, InvalidGraphResponseException.class, "Microsoft Graph token expired")
        );
    }

    @ParameterizedTest
    @MethodSource("testMemberValues")
    void getMembers(String pathToJson, Integer expectedSize, WireMockRuntimeInfo wmRuntimeInfo) throws IOException {
        String memberId = "memberId";
        mockGetMemberCall(memberId, pathToJson, 200);

        List<Member> memberList = createConnector(wmRuntimeInfo).getMembers(memberId);

        assertThat(memberList).hasSize(expectedSize);
    }

    @Test
    void getPaginatedMembers(WireMockRuntimeInfo wmRuntimeInfo) throws IOException {
        String memberId = "memberId";
        String baseUrl = wmRuntimeInfo.getHttpBaseUrl();
        mockGetMemberCallWithBody(memberId, getContent("groupMemberIdsPaginated.json").replace("http://localhost:1080", baseUrl), 200);
        mockGetMemberCall("next", "groupMemberIds.json", 200);

        List<Member> memberList = createConnector(wmRuntimeInfo).getMembers(memberId);

        assertThat(memberList).hasSize(8);
    }

    @Test
    void getMembersWithWrongStatusCode(WireMockRuntimeInfo wmRuntimeInfo) throws IOException {
        String memberId = "memberId";
        mockGetMemberCall(memberId, "emptyGroupMemberIds.json", 222);

        GraphConnector connector = createConnector(wmRuntimeInfo);
        assertThatThrownBy(() -> connector.getMembers(memberId))
                .isInstanceOf(InvalidGraphResponseException.class)
                .hasMessageContaining("Could not get proper response from Microsoft Graph");
    }

    @Test
    void getMembersResolvesCustomExtensionAttribute(WireMockRuntimeInfo wmRuntimeInfo) {
        String groupKey = "myGroup";
        String aadObjectId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
        String customAttribute = "extension_abc123def456_mycustomid";
        String customValue = "1234";

        FakeOAuth2SecurityConfiguration config = new FakeOAuth2SecurityConfiguration(customAttribute, "displayName", "mail");

        // Group endpoint returns just the AAD object ids of the members
        String groupBody = "{\"value\":[{\"@odata.type\":\"#microsoft.graph.user\",\"id\":\"" + aadObjectId + "\"}]}";
        mockGetMemberCallWithBody(groupKey, groupBody, 200);

        // /users/{id} returns the actual user object including the custom extension attribute
        String userBody = "{\"@odata.type\":\"#microsoft.graph.user\","
                + "\"" + customAttribute + "\":\"" + customValue + "\","
                + "\"displayName\":\"Some User\","
                + "\"mail\":\"some.user@example.com\"}";
        mockGetUserCallWithBody(aadObjectId, userBody, 200);

        GraphConnector connector = register(new GraphConnector(config, "test", null, wmRuntimeInfo.getHttpBaseUrl()));
        List<Member> members = connector.getMembers(groupKey);

        assertThat(members).hasSize(1);
        assertThat(members.get(0).getId()).isEqualTo(customValue);
        assertThat(members.get(0).getName()).isEqualTo("Some User");
        assertThat(members.get(0).getEmail()).isEqualTo("some.user@example.com");
    }

    @Test
    void getMembersResolvesNestedAttributePath(WireMockRuntimeInfo wmRuntimeInfo) {
        String groupKey = "myGroup";
        String aadObjectId = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
        String nestedPath = "onPremisesExtensionAttributes/extensionAttribute1";
        String expectedValue = "EMP-42";

        FakeOAuth2SecurityConfiguration config = new FakeOAuth2SecurityConfiguration(nestedPath, "displayName", "mail");

        String groupBody = "{\"value\":[{\"@odata.type\":\"#microsoft.graph.user\",\"id\":\"" + aadObjectId + "\"}]}";
        mockGetMemberCallWithBody(groupKey, groupBody, 200);

        String userBody = "{\"@odata.type\":\"#microsoft.graph.user\","
                + "\"onPremisesExtensionAttributes\":{\"extensionAttribute1\":\"" + expectedValue + "\"},"
                + "\"displayName\":\"Nested User\","
                + "\"mail\":\"nested.user@example.com\"}";
        mockGetUserCallWithBody(aadObjectId, userBody, 200);

        GraphConnector connector = register(new GraphConnector(config, "test", null, wmRuntimeInfo.getHttpBaseUrl()));
        List<Member> members = connector.getMembers(groupKey);

        assertThat(members).hasSize(1);
        assertThat(members.get(0).getId()).isEqualTo(expectedValue);
    }

    @Test
    void getMembersUsesGraphIdFieldOverrideVerbatim(WireMockRuntimeInfo wmRuntimeInfo) {
        // Customer scenario: 'mycustomid' is an OAuth2 claim name that Polarion extracts from the
        // token via authentication.xml <mapping><id>mycustomid</id></mapping>. In Microsoft Graph the
        // same logical identifier is stored in the standard built-in property
        // onPremisesSamAccountName — a completely different name. The graphIdField override lets
        // the operator decouple the two without touching authentication.xml. All resolved fields
        // are flat, so this routes through the batch path.
        String groupKey = "myGroup";
        String aadObjectId = "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee";
        String oauth2ClaimName = "megauid";
        String graphPropertyName = "onPremisesSamAccountName";
        String userValue = "usermegaid";

        FakeOAuth2SecurityConfiguration config = new FakeOAuth2SecurityConfiguration(oauth2ClaimName, "displayName", "mail");

        // The /groups/{id}/members batch response carries the fully-resolved user fields inline.
        String groupBody = "{\"value\":[{"
                + "\"@odata.type\":\"#microsoft.graph.user\","
                + "\"id\":\"" + aadObjectId + "\","
                + "\"" + graphPropertyName + "\":\"" + userValue + "\","
                + "\"displayName\":\"Override User\","
                + "\"mail\":\"override.user@example.com\""
                + "}]}";
        mockGetMemberCallWithBody(groupKey, groupBody, 200);

        GraphConnector connector = register(new GraphConnector(
                config, "test", new GraphFieldOverrides(graphPropertyName, null, null), wmRuntimeInfo.getHttpBaseUrl()));
        List<Member> members = connector.getMembers(groupKey);

        assertThat(members).hasSize(1);
        assertThat(members.get(0).getId()).isEqualTo(userValue);
        assertThat(members.get(0).getName()).isEqualTo("Override User");
        assertThat(members.get(0).getEmail()).isEqualTo("override.user@example.com");

        // The Graph $select on /groups/{id}/members must carry the override (onPremisesSamAccountName),
        // NOT the OAuth2 claim name (mycustomid). No per-user /users/{id} call is made.
        com.github.tomakehurst.wiremock.client.WireMock.verify(
                com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(urlPathEqualTo("/v1.0/groups/" + groupKey + "/members"))
                        .withQueryParam("$select", com.github.tomakehurst.wiremock.client.WireMock.containing(graphPropertyName))
                        .withQueryParam("$select", com.github.tomakehurst.wiremock.client.WireMock.notContaining(oauth2ClaimName)));
        com.github.tomakehurst.wiremock.client.WireMock.verify(0,
                com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(urlPathMatching("/v1\\.0/users/.+")));
    }

    @Test
    void resolveGraphFieldPrefersOverrideOverMapping() {
        // Confirms that when graphIdField / graphNameField / graphEmailField are set via job
        // parameters they replace the authentication.xml <mapping> value verbatim. Blank overrides
        // are treated as unset, and each field is resolved independently.
        FakeOAuth2SecurityConfiguration config = new FakeOAuth2SecurityConfiguration("mycustomid", "displayName", "mail");

        // No overrides → falls back to the mapping value as-is
        GraphConnector noOverrides = register(new GraphConnector(
                config, "test", null, "http://localhost"));
        assertThat(noOverrides.resolveGraphField("id", "mycustomid")).isEqualTo("mycustomid");

        // Override wins and is passed to Graph verbatim
        GraphConnector withOverride = register(new GraphConnector(
                config, "test", new GraphFieldOverrides("onPremisesSamAccountName", null, null), "http://localhost"));
        assertThat(withOverride.resolveGraphField("id", "mycustomid")).isEqualTo("onPremisesSamAccountName");

        // Blank override is treated as unset
        GraphConnector blankOverride = register(new GraphConnector(
                config, "test", new GraphFieldOverrides("   ", null, null), "http://localhost"));
        assertThat(blankOverride.resolveGraphField("id", "mycustomid")).isEqualTo("mycustomid");

        // Per-field independence: only the field with an override is replaced
        GraphConnector partial = register(new GraphConnector(
                config, "test", new GraphFieldOverrides("onPremisesSamAccountName", null, null), "http://localhost"));
        assertThat(partial.resolveGraphField("id", "mycustomid")).isEqualTo("onPremisesSamAccountName");
        assertThat(partial.resolveGraphField("name", "displayName")).isEqualTo("displayName");
        assertThat(partial.resolveGraphField("email", "mail")).isEqualTo("mail");
    }

    @Test
    void requiresPerUserFetchPicksBatchPathForFlatStandardProperties() {
        // Flat standard Graph properties — all routing to batch.
        assertThat(GraphConnector.requiresPerUserFetch("employeeId", "displayName", "mail")).isFalse();
        assertThat(GraphConnector.requiresPerUserFetch("mailNickname", "displayName", "mail")).isFalse();
        assertThat(GraphConnector.requiresPerUserFetch("id", null, null)).isFalse();
    }

    @Test
    void requiresPerUserFetchPicksPerUserPathForExtensionsOrNestedPaths() {
        // Any schema extension → per-user path (covers issue #74 edge case).
        assertThat(GraphConnector.requiresPerUserFetch("extension_abc_mycustomid", "displayName", "mail")).isTrue();
        assertThat(GraphConnector.requiresPerUserFetch("displayName", "extension_abc_name", "mail")).isTrue();
        assertThat(GraphConnector.requiresPerUserFetch("displayName", "displayName", "extension_abc_email")).isTrue();

        // Nested property paths (e.g. onPremisesExtensionAttributes/extensionAttribute1) → per-user.
        assertThat(GraphConnector.requiresPerUserFetch("onPremisesExtensionAttributes/extensionAttribute1", "displayName", "mail")).isTrue();
    }

    @Test
    void getMembersFollowsBatchPagination(WireMockRuntimeInfo wmRuntimeInfo) {
        // Batch path with @odata.nextLink on page 1. Covers the non-null branch of the nextLink
        // handling in collectUserMembers — the complementary missing-nextLink branch is covered
        // by every other batch test that uses a single-page response.
        String groupKey = "paginatedBatch";
        String baseUrl = wmRuntimeInfo.getHttpBaseUrl();
        String page1 = "{"
                + "\"@odata.nextLink\":\"" + baseUrl + "/v1.0/groups/paginatedBatch-page2/members\","
                + "\"value\":[{"
                + "\"@odata.type\":\"#microsoft.graph.user\","
                + "\"mailNickname\":\"page1.user\","
                + "\"displayName\":\"Page 1 User\","
                + "\"mail\":\"page1@example.com\""
                + "}]}";
        String page2 = "{\"value\":[{"
                + "\"@odata.type\":\"#microsoft.graph.user\","
                + "\"mailNickname\":\"page2.user\","
                + "\"displayName\":\"Page 2 User\","
                + "\"mail\":\"page2@example.com\""
                + "}]}";
        mockGetMemberCallWithBody(groupKey, page1, 200);
        mockGetMemberCallWithBody("paginatedBatch-page2", page2, 200);

        GraphConnector connector = register(new GraphConnector(
                new FakeOAuth2SecurityConfiguration(), "test", null, wmRuntimeInfo.getHttpBaseUrl()));
        List<Member> members = connector.getMembers(groupKey);

        assertThat(members).extracting(Member::getId).containsExactly("page1.user", "page2.user");
    }

    @Test
    void getMembersBatchPathTreatsJsonNullFieldValuesAsMissing(WireMockRuntimeInfo wmRuntimeInfo) {
        // Graph returns JSON null for optional properties that are not populated on a given user
        // (e.g. `"mail": null`). readTextField must collapse both missing keys and explicit JSON
        // nulls to the null reference — otherwise NullNode.asText() would surface as the literal
        // string "null" and Polarion user creation would use it as an actual mail address.
        String groupKey = "nullFieldsGroup";
        String body = "{\"value\":[{"
                + "\"@odata.type\":\"#microsoft.graph.user\","
                + "\"mailNickname\":\"partial.user\","
                + "\"displayName\":null,"
                + "\"mail\":null"
                + "}]}";
        mockGetMemberCallWithBody(groupKey, body, 200);

        GraphConnector connector = register(new GraphConnector(
                new FakeOAuth2SecurityConfiguration(), "test", null, wmRuntimeInfo.getHttpBaseUrl()));
        List<Member> members = connector.getMembers(groupKey);

        assertThat(members).hasSize(1);
        Member member = members.get(0);
        assertThat(member.getId()).isEqualTo("partial.user");
        assertThat(member.getName()).as("JSON null must surface as null, not the literal string 'null'").isNull();
        assertThat(member.getEmail()).isNull();
    }

    @Test
    void getRequestCountTracksGraphCallCount(WireMockRuntimeInfo wmRuntimeInfo) throws IOException {
        // The counter is exposed on IGraphConnector so the job can log total Graph load and
        // integration tests can verify batch-vs-per-user routing. Starts at 0, increments once
        // per logical fetchMSGraphApi call.
        mockGetGroupsCall("groups.json", 200);
        GraphConnector connector = createConnector(wmRuntimeInfo);

        assertThat(connector.getRequestCount()).isZero();

        connector.getGroups(groupPrefix);
        assertThat(connector.getRequestCount()).isEqualTo(1);

        connector.getGroups(groupPrefix);
        assertThat(connector.getRequestCount()).isEqualTo(2);
    }

    @Test
    void publicConstructorsInitializeConnectorWithoutThrowing() {
        // Exercises the production public constructors (the @VisibleForTesting variant is used
        // everywhere else in this suite). Only the construction path matters; the actual Graph
        // URL defaults to the real https://graph.microsoft.com endpoint which we never contact.
        FakeOAuth2SecurityConfiguration config = new FakeOAuth2SecurityConfiguration();

        GraphConnector minimal = register(new GraphConnector(config, "tok"));
        assertThat(minimal).isNotNull();

        GraphConnector withOverrides = register(new GraphConnector(
                config, "tok", new GraphFieldOverrides("onPremisesSamAccountName", null, null)));
        assertThat(withOverrides).isNotNull();
    }

    @Test
    void getGroups(WireMockRuntimeInfo wmRuntimeInfo) throws IOException {
        mockGetGroupsCall("groups.json", 200);

        List<Group> groupList = createConnector(wmRuntimeInfo).getGroups(groupPrefix);

        assertThat(groupList).hasSize(5);
    }

    @Test
    void getOrganizationData(WireMockRuntimeInfo wmRuntimeInfo) throws IOException {
        mockGetOrganizationDataCall("organizationData.json", 200);

        OrganizationData data = createConnector(wmRuntimeInfo).getOrganizationData();
        ObjectMapper mapper = new ObjectMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        OrganizationDataWrapper wrapper = mapper.readValue(getContent("organizationData.json"), OrganizationDataWrapper.class);

        assertThat(data).isEqualTo(wrapper.getValue().get(0));
    }

    @ParameterizedTest
    @MethodSource("testOrganizationValues")
    void getEmptyOrganizationDataResponse(Integer code, Class<?> clazz, String message, WireMockRuntimeInfo wmRuntimeInfo) throws IOException {
        mockGetOrganizationDataCall("emptyOrganizationData.json", code);

        GraphConnector connector = createConnector(wmRuntimeInfo);
        assertThatThrownBy(connector::getOrganizationData)
                .isInstanceOf(clazz)
                .hasMessageContaining(message);
    }

    @ParameterizedTest
    @MethodSource("testGroupValues")
    void getEmptyGroupsResponse(Integer code, Class<?> clazz, String message, WireMockRuntimeInfo wmRuntimeInfo) throws IOException {
        mockGetGroupsCall("emptyGroupsList.json", code);

        GraphConnector connector = createConnector(wmRuntimeInfo);
        assertThatThrownBy(() -> connector.getGroups(groupPrefix))
                .isInstanceOf(clazz)
                .hasMessageContaining(message);
    }

    @Test
    void retriesOnTooManyRequestsAndSucceeds(WireMockRuntimeInfo wmRuntimeInfo) throws IOException {
        String scenario = "throttled-groups";
        stubFor(get(urlPathEqualTo("/v1.0/groups"))
                .inScenario(scenario)
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "0"))
                .willSetStateTo("retried"));
        stubFor(get(urlPathEqualTo("/v1.0/groups"))
                .inScenario(scenario)
                .whenScenarioStateIs("retried")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBody(getContent("groups.json"))));

        List<Group> groups = createConnector(wmRuntimeInfo).getGroups(groupPrefix);

        assertThat(groups).hasSize(5);
        verify(2, getRequestedFor(urlPathEqualTo("/v1.0/groups")));
    }

    @Test
    void retriesOnServiceUnavailableAndSucceeds(WireMockRuntimeInfo wmRuntimeInfo) throws IOException {
        // Validates the end-to-end retry path on 503: first attempt fails, second succeeds.
        // Uses Retry-After: 0 explicitly so the connector does not actually sleep — the
        // exponential-backoff fallback (no Retry-After header) is exercised separately by the
        // pure unit tests on computeBackoffMillis below to keep this suite fast.
        String scenario = "unavailable-groups";
        stubFor(get(urlPathEqualTo("/v1.0/groups"))
                .inScenario(scenario)
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(503).withHeader("Retry-After", "0"))
                .willSetStateTo("recovered"));
        stubFor(get(urlPathEqualTo("/v1.0/groups"))
                .inScenario(scenario)
                .whenScenarioStateIs("recovered")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBody(getContent("groups.json"))));

        List<Group> groups = createConnector(wmRuntimeInfo).getGroups(groupPrefix);

        assertThat(groups).hasSize(5);
        verify(2, getRequestedFor(urlPathEqualTo("/v1.0/groups")));
    }

    private static Stream<Arguments> retryAfterHeaderCases() {
        // Header value → expected backoff at attempt=0. Covers both branches of
        // computeBackoffMillis: integer-seconds parsing (with capping at MAX_BACKOFF_MILLIS)
        // and the exponential-base fallback for missing / non-parseable / negative headers.
        // Each header value is wrapped in Named.named(...) so the display name lives next to
        // the value rather than as a separate (unused) test parameter.
        return Stream.of(
                Arguments.of(Named.named("integer seconds, well below cap",        "5"),                                5_000L),
                Arguments.of(Named.named("integer seconds, capped at MAX_BACKOFF", "9999"),                             60_000L),
                Arguments.of(Named.named("absent header → exponential base",       null),                               1_000L),
                Arguments.of(Named.named("HTTP-date format → exponential base",    "Wed, 21 Oct 2026 07:28:00 GMT"),    1_000L),
                Arguments.of(Named.named("negative value → exponential base",      "-1"),                               1_000L),
                Arguments.of(Named.named("blank string → exponential base",        "   "),                              1_000L)
        );
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("retryAfterHeaderCases")
    void computeBackoffMillisRespectsRetryAfterHeaderOrFallsBack(String headerValue, long expectedMillis) {
        Response response = mock(Response.class);
        when(response.getHeaderString(HttpHeaders.RETRY_AFTER)).thenReturn(headerValue);

        assertThat(GraphConnector.computeBackoffMillis(response, 0)).isEqualTo(expectedMillis);
    }

    @Test
    void computeBackoffMillisProducesExponentialSequenceCappedAtMax() {
        // Different axis from the parameterized test above: header is fixed (absent), the
        // attempt counter varies. Verifies the doubling sequence and the cap behaviour.
        Response response = mock(Response.class);
        when(response.getHeaderString(HttpHeaders.RETRY_AFTER)).thenReturn(null);

        assertThat(GraphConnector.computeBackoffMillis(response, 0)).isEqualTo(1_000L);
        assertThat(GraphConnector.computeBackoffMillis(response, 1)).isEqualTo(2_000L);
        assertThat(GraphConnector.computeBackoffMillis(response, 2)).isEqualTo(4_000L);
        assertThat(GraphConnector.computeBackoffMillis(response, 3)).isEqualTo(8_000L);
        assertThat(GraphConnector.computeBackoffMillis(response, 4)).isEqualTo(16_000L);
        assertThat(GraphConnector.computeBackoffMillis(response, 10)).isEqualTo(60_000L);
    }

    @Test
    void givesUpAfterMaxRetriesOnPersistentThrottling(WireMockRuntimeInfo wmRuntimeInfo) {
        stubFor(get(urlPathEqualTo("/v1.0/groups"))
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "0")));

        GraphConnector connector = createConnector(wmRuntimeInfo);
        assertThatThrownBy(() -> connector.getGroups(groupPrefix))
                .isInstanceOf(InvalidGraphResponseException.class)
                .hasMessageContaining("Could not get proper response from Microsoft Graph");

        // MAX_RETRIES = 5, so the total number of attempts is 6.
        verify(6, getRequestedFor(urlPathEqualTo("/v1.0/groups")));
    }

    @Test
    void getMembersFiltersOutNonUserDirectoryObjects(WireMockRuntimeInfo wmRuntimeInfo) throws IOException {
        // /groups/{id}/members can return any directory object: users, nested groups, service
        // principals, devices. Only #microsoft.graph.user entries must end up in the result —
        // everything else would crash downstream Polarion user creation. Fixture has 6 members
        // of mixed types; exactly the 3 user-typed ones should survive. Uses the batch path
        // (default mapping is flat), so filtering happens on the projected /members response.
        String groupKey = "groupWithMixedMembers";
        FakeOAuth2SecurityConfiguration config = new FakeOAuth2SecurityConfiguration("id", "displayName", "mail");
        mockGetMemberCall(groupKey, "groupMembersMixedTypes.json", 200);

        GraphConnector connector = register(new GraphConnector(
                config, "test", null, wmRuntimeInfo.getHttpBaseUrl()));
        List<Member> members = connector.getMembers(groupKey);

        assertThat(members).hasSize(3);
        assertThat(members).extracting(Member::getId).containsExactly(
                "11111111-1111-1111-1111-111111111111",
                "33333333-3333-3333-3333-333333333333",
                "66666666-6666-6666-6666-666666666666");
        // Batch path makes exactly one call to /members and no per-user calls.
        verify(1, getRequestedFor(urlPathEqualTo("/v1.0/groups/" + groupKey + "/members")));
        verify(0, getRequestedFor(urlPathMatching("/v1\\.0/users/.+")));
    }

    @Test
    void getMembersParsesRealisticUserResponseWithExtensionAttributes(WireMockRuntimeInfo wmRuntimeInfo) throws IOException {
        // The fixture mirrors the actual /users/{id}/$entity shape returned by Microsoft Graph
        // when extension property names are used in $select: a flat object with @odata.context,
        // standard fields (id, displayName, userPrincipalName) and extension_<appIdNoDashes>_<name>
        // properties at the top level. No 'value' wrapper.
        String groupKey = "myGroup";
        String aadObjectId = "11111111-1111-1111-1111-111111111111";
        // The bare names that appear in authentication.xml — used by Polarion at login time
        // to read claims from the OAuth2 token.
        String mappingId = "mycustomid";
        String mappingName = "name";
        String mappingEmail = "email";
        // The fully-qualified Graph property names that the job parameters point at. Schema
        // extensions are referenced in Graph as extension_<appIdNoDashes>_<field>; the fixture
        // uses aaaaaaaabbbbccccddddeeeeeeeeeeee as the owning app id.
        String graphId = "extension_aaaaaaaabbbbccccddddeeeeeeeeeeee_mycustomid";
        String graphName = "extension_aaaaaaaabbbbccccddddeeeeeeeeeeee_name";
        String graphEmail = "extension_aaaaaaaabbbbccccddddeeeeeeeeeeee_email";

        FakeOAuth2SecurityConfiguration config = new FakeOAuth2SecurityConfiguration(mappingId, mappingName, mappingEmail);

        // Group endpoint returns just the AAD object id.
        String groupBody = "{\"value\":[{\"@odata.type\":\"#microsoft.graph.user\",\"id\":\"" + aadObjectId + "\"}]}";
        mockGetMemberCallWithBody(groupKey, groupBody, 200);
        // /users/{id} returns the realistic Graph response loaded from the fixture.
        mockGetUserCallWithBody(aadObjectId, getContent("userWithExtensionAttributes.json"), 200);

        GraphConnector connector = register(new GraphConnector(config, "test",
                new GraphFieldOverrides(graphId, graphName, graphEmail), wmRuntimeInfo.getHttpBaseUrl()));
        List<Member> members = connector.getMembers(groupKey);

        assertThat(members).hasSize(1);
        Member member = members.get(0);
        assertThat(member.getId()).isEqualTo("anonymous-id-001");
        assertThat(member.getName()).isEqualTo("John Doe");
        assertThat(member.getEmail()).isEqualTo("john.doe@example.com");

        // The Graph $select must reference the fully-qualified extension property names — that's
        // exactly what makes the difference between issue #74 and a working request.
        verify(getRequestedFor(urlPathEqualTo("/v1.0/users/" + aadObjectId))
                .withQueryParam("$select", com.github.tomakehurst.wiremock.client.WireMock.containing(graphId))
                .withQueryParam("$select", com.github.tomakehurst.wiremock.client.WireMock.containing(graphName))
                .withQueryParam("$select", com.github.tomakehurst.wiremock.client.WireMock.containing(graphEmail)));
    }

    @Test
    void getMembersParsesRealisticUserResponseWithVanillaAttributes(WireMockRuntimeInfo wmRuntimeInfo) {
        // Vanilla MS Graph properties: mailNickname/displayName/mail. All flat → batch path.
        // Validates that the projected user fields are picked up inline from the /members response.
        String groupKey = "myGroup";
        String aadObjectId = "11111111-1111-1111-1111-111111111111";

        // Default FakeOAuth2SecurityConfiguration() uses mailNickname/displayName/mail.
        FakeOAuth2SecurityConfiguration config = new FakeOAuth2SecurityConfiguration();

        String groupBody = "{\"value\":[{"
                + "\"@odata.type\":\"#microsoft.graph.user\","
                + "\"id\":\"" + aadObjectId + "\","
                + "\"mailNickname\":\"john.doe\","
                + "\"displayName\":\"John Doe\","
                + "\"mail\":\"john.doe@example.com\""
                + "}]}";
        mockGetMemberCallWithBody(groupKey, groupBody, 200);

        GraphConnector connector = register(new GraphConnector(config, "test", null, wmRuntimeInfo.getHttpBaseUrl()));
        List<Member> members = connector.getMembers(groupKey);

        assertThat(members).hasSize(1);
        Member member = members.get(0);
        assertThat(member.getId()).isEqualTo("john.doe");
        assertThat(member.getName()).isEqualTo("John Doe");
        assertThat(member.getEmail()).isEqualTo("john.doe@example.com");
        // Single batch call, no per-user calls.
        verify(0, getRequestedFor(urlPathMatching("/v1\\.0/users/.+")));
    }

    @Test
    void getMembersHandlesUserWithMissingExtensionValue(WireMockRuntimeInfo wmRuntimeInfo) throws IOException {
        // The customer's extension property exists on the schema but the user has not been
        // assigned a value for it (Graph returns the field with literal JSON null). The parser
        // must produce a Member with id == null without crashing — GraphService will then drop
        // it from the synchronization set via its non-null filter. Other resolved fields must
        // still come through normally.
        String groupKey = "myGroup";
        String aadObjectId = "11111111-1111-1111-1111-111111111111";
        String graphId = "extension_aaaaaaaabbbbccccddddeeeeeeeeeeee_mycustomid";
        String graphName = "extension_aaaaaaaabbbbccccddddeeeeeeeeeeee_name";
        String graphEmail = "extension_aaaaaaaabbbbccccddddeeeeeeeeeeee_email";

        FakeOAuth2SecurityConfiguration config = new FakeOAuth2SecurityConfiguration("mycustomid", "name", "email");

        String groupBody = "{\"value\":[{\"@odata.type\":\"#microsoft.graph.user\",\"id\":\"" + aadObjectId + "\"}]}";
        mockGetMemberCallWithBody(groupKey, groupBody, 200);
        mockGetUserCallWithBody(aadObjectId, getContent("userWithMissingExtensionValue.json"), 200);

        GraphConnector connector = register(new GraphConnector(config, "test",
                new GraphFieldOverrides(graphId, graphName, graphEmail), wmRuntimeInfo.getHttpBaseUrl()));
        List<Member> members = connector.getMembers(groupKey);

        assertThat(members).hasSize(1);
        Member member = members.get(0);
        assertThat(member.getId()).isNull();
        assertThat(member.getName()).isEqualTo("John Doe");
        assertThat(member.getEmail()).isEqualTo("john.doe@example.com");
    }

    @Test
    void getMembersHandlesMinimalUserEntityWhenSelectIsSilentlyDropped(WireMockRuntimeInfo wmRuntimeInfo) throws IOException {
        // When $select references property names that Graph does not understand (the issue #74
        // failure mode, but at the per-user endpoint), Graph returns a /users/{id}/$entity body
        // containing only @odata.context — no id, no @odata.type, none of the requested fields.
        // The parser must not crash. All Member fields end up null. GraphService.getAadMemberIds
        // would silently drop this user via its non-null id filter, which is the silent failure
        // mode this test pins down — anyone breaking the field resolution logic will see this
        // test fail with a clear pointer to the regression.
        String groupKey = "myGroup";
        String aadObjectId = "11111111-1111-1111-1111-111111111111";

        // Customer-style mapping with bare extension names that Graph does not understand as-is
        // (no graphIdField override applied), so Graph silently drops them from the response.
        FakeOAuth2SecurityConfiguration config = new FakeOAuth2SecurityConfiguration("mycustomid", "name", "email");

        String groupBody = "{\"value\":[{\"@odata.type\":\"#microsoft.graph.user\",\"id\":\"" + aadObjectId + "\"}]}";
        mockGetMemberCallWithBody(groupKey, groupBody, 200);
        mockGetUserCallWithBody(aadObjectId, getContent("userMinimalEntity.json"), 200);

        GraphConnector connector = register(new GraphConnector(config, "test", null, wmRuntimeInfo.getHttpBaseUrl()));
        List<Member> members = connector.getMembers(groupKey);

        assertThat(members).hasSize(1);
        Member member = members.get(0);
        assertThat(member.getId()).isNull();
        assertThat(member.getName()).isNull();
        assertThat(member.getEmail()).isNull();
    }

    @Test
    void wrongJsonDataResponse(WireMockRuntimeInfo wmRuntimeInfo) throws IOException {
        String memberId = "memberId";

        mockGetGroupsCall("wrongJsonData.json", 200);
        mockGetOrganizationDataCall("wrongJsonData.json", 200);
        mockGetMemberCall(memberId, "wrongJsonData.json", 200);

        GraphConnector connector = createConnector(wmRuntimeInfo);
        assertThatThrownBy(() -> connector.getGroups(groupPrefix))
                .isInstanceOf(ResponseParsingException.class);
        assertThatThrownBy(connector::getOrganizationData)
                .isInstanceOf(ResponseParsingException.class);
        // After the @odata.type filtering refactor, fetchGroupMemberObjectIds parses the response
        // body itself and wraps any JsonProcessingException in ResponseParsingException — same as
        // getGroups/getOrganizationData. Previously the leaf parser leaked JsonParseException
        // through, which was inconsistent with the rest of the connector API.
        assertThatThrownBy(() -> connector.getMembers(memberId))
                .isInstanceOf(ResponseParsingException.class);
    }

    private void mockGetGroupsCall(String path, Integer statusCode) throws IOException {
        stubFor(get(urlPathEqualTo("/v1.0/groups"))
                .willReturn(aResponse()
                        .withStatus(statusCode)
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBody(getContent(path))));
    }

    private void mockGetOrganizationDataCall(String path, Integer statusCode) throws IOException {
        stubFor(get(urlPathEqualTo("/v1.0/organization"))
                .willReturn(aResponse()
                        .withStatus(statusCode)
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBody(getContent(path))));
    }

    private void mockGetMemberCall(String memberId, String path, Integer statusCode) throws IOException {
        mockGetMemberCallWithBody(memberId, getContent(path), statusCode);
    }

    private void mockGetMemberCallWithBody(String memberId, String body, Integer statusCode) {
        stubFor(get(urlPathEqualTo("/v1.0/groups/" + memberId + "/members"))
                .willReturn(aResponse()
                        .withStatus(statusCode)
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBody(body)));
    }

    private void mockGetUserCallWithBody(String userId, String body, Integer statusCode) {
        stubFor(get(urlPathEqualTo("/v1.0/users/" + userId))
                .willReturn(aResponse()
                        .withStatus(statusCode)
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBody(body)));
    }

    private String getContent(String path) throws IOException {
        String content;
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(path)) {
            if (inputStream == null) {
                throw new IllegalArgumentException("Resource not found: " + path);
            }
            content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
        Assertions.assertNotNull(content);
        return content;
    }

}
