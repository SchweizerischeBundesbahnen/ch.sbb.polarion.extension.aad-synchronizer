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
import java.util.List;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
@WireMockTest
class GraphConnectorTest {

    private final IOAuth2SecurityConfiguration authenticationProviderConfiguration = new FakeOAuth2SecurityConfiguration();
    private final String groupPrefix = "test";

    private GraphConnector createConnector(WireMockRuntimeInfo wmRuntimeInfo) {
        return new GraphConnector(authenticationProviderConfiguration, "test", wmRuntimeInfo.getHttpBaseUrl());
    }

    private static Stream<Arguments> testMemberValues() {
        return Stream.of(
                Arguments.of("members.json", 5),
                Arguments.of("emptyMembersList.json", 0)
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
        mockGetMemberCallWithBody(memberId, getContent("membersPaginated.json").replace("http://localhost:1080", baseUrl), 200);
        mockGetMemberCall("next", "members.json", 200);

        List<Member> memberList = createConnector(wmRuntimeInfo).getMembers(memberId);

        assertThat(memberList).hasSize(8);
    }

    @Test
    void getMembersWithWrongStatusCode(WireMockRuntimeInfo wmRuntimeInfo) throws IOException {
        String memberId = "memberId";
        mockGetMemberCall(memberId, "emptyMembersList.json", 222);

        GraphConnector connector = createConnector(wmRuntimeInfo);
        assertThatThrownBy(() -> connector.getMembers(memberId))
                .isInstanceOf(InvalidGraphResponseException.class)
                .hasMessageContaining("Could not get proper response from Microsoft Graph");
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
        assertThatThrownBy(() -> connector.getMembers(memberId))
                .isInstanceOf(JsonParseException.class);
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
