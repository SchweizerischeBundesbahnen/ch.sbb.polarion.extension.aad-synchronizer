package ch.sbb.polarion.extension.aad.synchronizer.connector;

import ch.sbb.polarion.extension.aad.synchronizer.exception.InvalidGraphResponseException;
import ch.sbb.polarion.extension.aad.synchronizer.exception.NotFoundException;
import ch.sbb.polarion.extension.aad.synchronizer.exception.ResponseParsingException;
import ch.sbb.polarion.extension.aad.synchronizer.model.Group;
import ch.sbb.polarion.extension.aad.synchronizer.model.Member;
import ch.sbb.polarion.extension.aad.synchronizer.model.OrganizationData;
import ch.sbb.polarion.extension.aad.synchronizer.model.OrganizationDataWrapper;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.polarion.core.config.IOAuth2SecurityConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;

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
        return register(new GraphConnector(authenticationProviderConfiguration, "test", null, null, wmRuntimeInfo.getHttpBaseUrl()));
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
        stubAnyUserCallWithMailNickname();

        List<Member> memberList = createConnector(wmRuntimeInfo).getMembers(memberId);

        assertThat(memberList).hasSize(expectedSize);
    }

    @Test
    void getPaginatedMembers(WireMockRuntimeInfo wmRuntimeInfo) throws IOException {
        String memberId = "memberId";
        String baseUrl = wmRuntimeInfo.getHttpBaseUrl();
        mockGetMemberCallWithBody(memberId, getContent("groupMemberIdsPaginated.json").replace("http://localhost:1080", baseUrl), 200);
        mockGetMemberCall("next", "groupMemberIds.json", 200);
        stubAnyUserCallWithMailNickname();

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
    void getMembersResolvesCustomExtensionAttribute(WireMockRuntimeInfo wmRuntimeInfo) throws IOException {
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

        GraphConnector connector = register(new GraphConnector(config, "test", null, null, wmRuntimeInfo.getHttpBaseUrl()));
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

        GraphConnector connector = register(new GraphConnector(config, "test", null, null, wmRuntimeInfo.getHttpBaseUrl()));
        List<Member> members = connector.getMembers(groupKey);

        assertThat(members).hasSize(1);
        assertThat(members.get(0).getId()).isEqualTo(expectedValue);
    }

    @Test
    void getMembersExpandsBareAttributeUsingExtensionAppId(WireMockRuntimeInfo wmRuntimeInfo) {
        String groupKey = "myGroup";
        String aadObjectId = "cccccccc-cccc-cccc-cccc-cccccccccccc";
        // The user puts only the bare claim name in authentication.xml...
        String bareAttribute = "mycustomid";
        // ...and the AAD application id (with dashes) in the extensionAppId job parameter.
        String appId = "abc123de-f456-7890-abcd-ef1234567890";
        String appIdNoDashes = "abc123def4567890abcdef1234567890";
        String expandedAttribute = "extension_" + appIdNoDashes + "_" + bareAttribute;
        String customValue = "9876";

        FakeOAuth2SecurityConfiguration config = new FakeOAuth2SecurityConfiguration(bareAttribute, "displayName", "mail");

        String groupBody = "{\"value\":[{\"@odata.type\":\"#microsoft.graph.user\",\"id\":\"" + aadObjectId + "\"}]}";
        mockGetMemberCallWithBody(groupKey, groupBody, 200);

        // The /users/{id} response uses the fully-qualified Graph property name
        String userBody = "{\"@odata.type\":\"#microsoft.graph.user\","
                + "\"" + expandedAttribute + "\":\"" + customValue + "\","
                + "\"displayName\":\"Bare User\","
                + "\"mail\":\"bare.user@example.com\"}";
        mockGetUserCallWithBody(aadObjectId, userBody, 200);

        // extensionFields not set → defaults to "id" so the bare attribute gets expanded
        GraphConnector connector = register(new GraphConnector(config, "test", appId, null, wmRuntimeInfo.getHttpBaseUrl()));
        List<Member> members = connector.getMembers(groupKey);

        assertThat(members).hasSize(1);
        assertThat(members.get(0).getId()).isEqualTo(customValue);
        // The Graph $select query should also have used the expanded attribute name
        com.github.tomakehurst.wiremock.client.WireMock.verify(
                com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(urlPathEqualTo("/v1.0/users/" + aadObjectId))
                        .withQueryParam("$select", com.github.tomakehurst.wiremock.client.WireMock.containing(expandedAttribute)));
    }

    @Test
    void getMembersExpandsMultipleFieldsViaExtensionFields(WireMockRuntimeInfo wmRuntimeInfo) {
        String groupKey = "myGroup";
        String aadObjectId = "dddddddd-dddd-dddd-dddd-dddddddddddd";
        String appId = "abc-123";
        String appIdNoDashes = "abc123";
        // id and email are custom extensions; name stays standard
        String bareId = "mycustomid";
        String bareEmail = "myworkmail";
        String expandedId = "extension_" + appIdNoDashes + "_" + bareId;
        String expandedEmail = "extension_" + appIdNoDashes + "_" + bareEmail;

        FakeOAuth2SecurityConfiguration config = new FakeOAuth2SecurityConfiguration(bareId, "displayName", bareEmail);

        String groupBody = "{\"value\":[{\"@odata.type\":\"#microsoft.graph.user\",\"id\":\"" + aadObjectId + "\"}]}";
        mockGetMemberCallWithBody(groupKey, groupBody, 200);

        String userBody = "{\"@odata.type\":\"#microsoft.graph.user\","
                + "\"" + expandedId + "\":\"the-id\","
                + "\"displayName\":\"Multi User\","
                + "\"" + expandedEmail + "\":\"work@example.com\"}";
        mockGetUserCallWithBody(aadObjectId, userBody, 200);

        GraphConnector connector = register(new GraphConnector(config, "test", appId, "id, email", wmRuntimeInfo.getHttpBaseUrl()));
        List<Member> members = connector.getMembers(groupKey);

        assertThat(members).hasSize(1);
        assertThat(members.get(0).getId()).isEqualTo("the-id");
        assertThat(members.get(0).getName()).isEqualTo("Multi User");
        assertThat(members.get(0).getEmail()).isEqualTo("work@example.com");
        // Both expanded names must be present in the $select query, displayName must remain standard
        com.github.tomakehurst.wiremock.client.WireMock.verify(
                com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(urlPathEqualTo("/v1.0/users/" + aadObjectId))
                        .withQueryParam("$select", com.github.tomakehurst.wiremock.client.WireMock.containing(expandedId))
                        .withQueryParam("$select", com.github.tomakehurst.wiremock.client.WireMock.containing("displayName"))
                        .withQueryParam("$select", com.github.tomakehurst.wiremock.client.WireMock.containing(expandedEmail)));
    }

    @Test
    void expandExtensionAttributeBehaviour() {
        FakeOAuth2SecurityConfiguration config = new FakeOAuth2SecurityConfiguration();
        // No extensionAppId → bare name passes through unchanged
        GraphConnector noAppIdConnector = register(new GraphConnector(config, "test", null, null, "http://localhost"));
        assertThat(noAppIdConnector.expandExtensionAttribute("mycustomid")).isEqualTo("mycustomid");

        // With extensionAppId → bare name gets expanded, dashes stripped from appId
        GraphConnector withAppIdConnector = register(new GraphConnector(config, "test", "abc-123-def", null, "http://localhost"));
        assertThat(withAppIdConnector.expandExtensionAttribute("mycustomid")).isEqualTo("extension_abc123def_mycustomid");
        // Already-qualified name is not double-prefixed
        assertThat(withAppIdConnector.expandExtensionAttribute("extension_xxx_yyy")).isEqualTo("extension_xxx_yyy");
        // Nested path is left untouched
        assertThat(withAppIdConnector.expandExtensionAttribute("onPremisesExtensionAttributes/extensionAttribute1"))
                .isEqualTo("onPremisesExtensionAttributes/extensionAttribute1");
    }

    @Test
    void expandIfMarkedRespectsExtensionFields() {
        FakeOAuth2SecurityConfiguration config = new FakeOAuth2SecurityConfiguration();

        // Default: extensionFields unset and extensionAppId set → defaults to {id}
        GraphConnector defaultConnector = register(new GraphConnector(config, "test", "abc-123", null, "http://localhost"));
        assertThat(defaultConnector.expandIfMarked("id", "mycustomid")).isEqualTo("extension_abc123_mycustomid");
        assertThat(defaultConnector.expandIfMarked("email", "mail")).isEqualTo("mail");

        // Explicit list of fields
        GraphConnector multiConnector = register(new GraphConnector(config, "test", "abc-123", "id,email", "http://localhost"));
        assertThat(multiConnector.expandIfMarked("id", "mycustomid")).isEqualTo("extension_abc123_mycustomid");
        assertThat(multiConnector.expandIfMarked("name", "displayName")).isEqualTo("displayName");
        assertThat(multiConnector.expandIfMarked("email", "myworkmail")).isEqualTo("extension_abc123_myworkmail");

        // No appId, no fields → nothing happens
        GraphConnector noopConnector = register(new GraphConnector(config, "test", null, null, "http://localhost"));
        assertThat(noopConnector.expandIfMarked("id", "mycustomid")).isEqualTo("mycustomid");
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
    void retriesOnServiceUnavailableWithoutRetryAfterHeader(WireMockRuntimeInfo wmRuntimeInfo) throws IOException {
        String scenario = "unavailable-groups";
        stubFor(get(urlPathEqualTo("/v1.0/groups"))
                .inScenario(scenario)
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("recovered"));
        stubFor(get(urlPathEqualTo("/v1.0/groups"))
                .inScenario(scenario)
                .whenScenarioStateIs("recovered")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBody(getContent("groups.json"))));

        // Without Retry-After the connector falls back to its 1s exponential backoff base — that's
        // an acceptable test cost (single sleep) versus introducing a clock seam just for tests.
        List<Group> groups = createConnector(wmRuntimeInfo).getGroups(groupPrefix);

        assertThat(groups).hasSize(5);
        verify(2, getRequestedFor(urlPathEqualTo("/v1.0/groups")));
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
        // principals, devices. Only #microsoft.graph.user ids must propagate to /users/{id} —
        // everything else would 404 there and crash the entire job. Fixture has 6 members of
        // mixed types; only the 3 user-typed ones should be followed up.
        String groupKey = "groupWithMixedMembers";
        mockGetMemberCall(groupKey, "groupMembersMixedTypes.json", 200);
        stubAnyUserCallWithMailNickname();

        List<Member> members = createConnector(wmRuntimeInfo).getMembers(groupKey);

        assertThat(members).hasSize(3);

        // Positive proof: WireMock saw /users/{id} requests for the user ids only.
        verify(1, getRequestedFor(urlPathEqualTo("/v1.0/users/11111111-1111-1111-1111-111111111111")));
        verify(1, getRequestedFor(urlPathEqualTo("/v1.0/users/33333333-3333-3333-3333-333333333333")));
        verify(1, getRequestedFor(urlPathEqualTo("/v1.0/users/66666666-6666-6666-6666-666666666666")));
        // Negative proof: nested group / service principal / device ids must not be requested.
        verify(0, getRequestedFor(urlPathEqualTo("/v1.0/users/22222222-2222-2222-2222-222222222222")));
        verify(0, getRequestedFor(urlPathEqualTo("/v1.0/users/44444444-4444-4444-4444-444444444444")));
        verify(0, getRequestedFor(urlPathEqualTo("/v1.0/users/55555555-5555-5555-5555-555555555555")));
    }

    @Test
    void getMembersParsesRealisticUserResponseWithExtensionAttributes(WireMockRuntimeInfo wmRuntimeInfo) throws IOException {
        // The fixture mirrors the actual /users/{id}/$entity shape returned by Microsoft Graph
        // when extension property names are used in $select: a flat object with @odata.context,
        // standard fields (id, displayName, userPrincipalName) and extension_<appIdNoDashes>_<name>
        // properties at the top level. No 'value' wrapper.
        String groupKey = "myGroup";
        String aadObjectId = "11111111-1111-1111-1111-111111111111";
        // The bare names that appear in authentication.xml — same as the customer scenario.
        String mappingId = "mycustomid";
        String mappingName = "name";
        String mappingEmail = "email";
        // The extension owner app id (with dashes) configured as extensionAppId job parameter.
        // Stripping dashes yields aaaaaaaabbbbccccddddeeeeeeeeeeee, which the fixture uses.
        String extensionAppId = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";

        FakeOAuth2SecurityConfiguration config = new FakeOAuth2SecurityConfiguration(mappingId, mappingName, mappingEmail);

        // Group endpoint returns just the AAD object id.
        String groupBody = "{\"value\":[{\"@odata.type\":\"#microsoft.graph.user\",\"id\":\"" + aadObjectId + "\"}]}";
        mockGetMemberCallWithBody(groupKey, groupBody, 200);
        // /users/{id} returns the realistic Graph response loaded from the fixture.
        mockGetUserCallWithBody(aadObjectId, getContent("userWithExtensionAttributes.json"), 200);

        GraphConnector connector = register(new GraphConnector(config, "test", extensionAppId, "id,name,email", wmRuntimeInfo.getHttpBaseUrl()));
        List<Member> members = connector.getMembers(groupKey);

        assertThat(members).hasSize(1);
        Member member = members.get(0);
        assertThat(member.getId()).isEqualTo("anonymous-id-001");
        assertThat(member.getName()).isEqualTo("John Doe");
        assertThat(member.getEmail()).isEqualTo("john.doe@example.com");

        // The Graph $select must reference the fully-expanded extension property names — that's
        // exactly what makes the difference between issue #74 and a working request.
        String expandedId = "extension_aaaaaaaabbbbccccddddeeeeeeeeeeee_mycustomid";
        String expandedName = "extension_aaaaaaaabbbbccccddddeeeeeeeeeeee_name";
        String expandedEmail = "extension_aaaaaaaabbbbccccddddeeeeeeeeeeee_email";
        verify(getRequestedFor(urlPathEqualTo("/v1.0/users/" + aadObjectId))
                .withQueryParam("$select", com.github.tomakehurst.wiremock.client.WireMock.containing(expandedId))
                .withQueryParam("$select", com.github.tomakehurst.wiremock.client.WireMock.containing(expandedName))
                .withQueryParam("$select", com.github.tomakehurst.wiremock.client.WireMock.containing(expandedEmail)));
    }

    @Test
    void getMembersParsesRealisticUserResponseWithVanillaAttributes(WireMockRuntimeInfo wmRuntimeInfo) throws IOException {
        // Vanilla MS Graph properties: mailNickname/displayName/mail. No extensionAppId, no
        // extensionFields — bare names go straight into $select. Validates the simplest possible
        // synchronizer configuration end-to-end through the parser.
        String groupKey = "myGroup";
        String aadObjectId = "11111111-1111-1111-1111-111111111111";

        // Default FakeOAuth2SecurityConfiguration() uses mailNickname/displayName/mail.
        FakeOAuth2SecurityConfiguration config = new FakeOAuth2SecurityConfiguration();

        String groupBody = "{\"value\":[{\"@odata.type\":\"#microsoft.graph.user\",\"id\":\"" + aadObjectId + "\"}]}";
        mockGetMemberCallWithBody(groupKey, groupBody, 200);
        mockGetUserCallWithBody(aadObjectId, getContent("userWithVanillaAttributes.json"), 200);

        GraphConnector connector = register(new GraphConnector(config, "test", null, null, wmRuntimeInfo.getHttpBaseUrl()));
        List<Member> members = connector.getMembers(groupKey);

        assertThat(members).hasSize(1);
        Member member = members.get(0);
        assertThat(member.getId()).isEqualTo("john.doe");
        assertThat(member.getName()).isEqualTo("John Doe");
        assertThat(member.getEmail()).isEqualTo("john.doe@example.com");
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
        String extensionAppId = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";

        FakeOAuth2SecurityConfiguration config = new FakeOAuth2SecurityConfiguration("mycustomid", "name", "email");

        String groupBody = "{\"value\":[{\"@odata.type\":\"#microsoft.graph.user\",\"id\":\"" + aadObjectId + "\"}]}";
        mockGetMemberCallWithBody(groupKey, groupBody, 200);
        mockGetUserCallWithBody(aadObjectId, getContent("userWithMissingExtensionValue.json"), 200);

        GraphConnector connector = register(new GraphConnector(config, "test", extensionAppId, "id,name,email", wmRuntimeInfo.getHttpBaseUrl()));
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
        // mode this test pins down — anyone breaking the extension expansion logic will see this
        // test fail with a clear pointer to the regression.
        String groupKey = "myGroup";
        String aadObjectId = "11111111-1111-1111-1111-111111111111";

        // Customer-style mapping but WITHOUT extensionAppId — so expandIfMarked returns the bare
        // names unchanged, and Graph silently drops them.
        FakeOAuth2SecurityConfiguration config = new FakeOAuth2SecurityConfiguration("mycustomid", "name", "email");

        String groupBody = "{\"value\":[{\"@odata.type\":\"#microsoft.graph.user\",\"id\":\"" + aadObjectId + "\"}]}";
        mockGetMemberCallWithBody(groupKey, groupBody, 200);
        mockGetUserCallWithBody(aadObjectId, getContent("userMinimalEntity.json"), 200);

        GraphConnector connector = register(new GraphConnector(config, "test", null, null, wmRuntimeInfo.getHttpBaseUrl()));
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

    /**
     * Stubs every {@code /v1.0/users/{id}} request with a response containing the standard
     * mailNickname/displayName/mail fields the default {@link FakeOAuth2SecurityConfiguration} expects.
     * Used by tests that don't care about per-user details, only that the right number of members is returned.
     */
    private void stubAnyUserCallWithMailNickname() {
        stubFor(get(urlPathMatching("/v1\\.0/users/.+"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        .withBody("{\"@odata.type\":\"#microsoft.graph.user\","
                                + "\"mailNickname\":\"someUser\","
                                + "\"displayName\":\"Some User\","
                                + "\"mail\":\"some.user@example.com\"}")));
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
