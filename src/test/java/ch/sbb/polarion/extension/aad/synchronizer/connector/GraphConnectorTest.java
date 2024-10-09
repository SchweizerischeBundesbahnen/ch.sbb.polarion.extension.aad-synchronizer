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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.Header;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.matchers.Times.exactly;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

@ExtendWith(MockitoExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GraphConnectorTest {

    private final GraphConnector graphConnector = new GraphConnector("test", "http://localhost:1080");
    private final String groupPrefix = "test";
    protected ClientAndServer mockServer;


    private static Stream<Arguments> testMemberValues() {

        return Stream.of(
                Arguments.of("members.json", 5),
                Arguments.of("emptyMembersList.json", 0)
        );
    }

    private static Stream<Arguments> testGroupValues() {

        return Stream.of(
                Arguments.of(200, NotFoundException.class, "No AAD groups were found"),
                Arguments.of(222, InvalidGraphResponseException.class, "Could not get proper response from Microsoft Graph for the GroupResponseWrapper"),
                Arguments.of(401, InvalidGraphResponseException.class, "Microsoft Graph token expired")
        );
    }
    private static Stream<Arguments> testOrganizationValues() {

        return Stream.of(
                Arguments.of(200, NotFoundException.class, "No Organization data was found"),
                Arguments.of(222, InvalidGraphResponseException.class, "Could not get proper response from Microsoft Graph for the OrganizationDataWrapper"),
                Arguments.of(401, InvalidGraphResponseException.class, "Microsoft Graph token expired")
        );
    }

    @BeforeAll
    public void startServer() {
        mockServer = startClientAndServer(1080);
    }

    @AfterAll
    public void stopServer() {
        mockServer.stop();
    }

    @ParameterizedTest
    @MethodSource("testMemberValues")
    void getMembers(String pathToJson, Integer expectedSize) throws IOException {
        String memberId = "memberId";
        mockGetMemberCall(memberId, pathToJson, 200);

        List<Member> memberList = graphConnector.getMembers(memberId);

        assertThat(memberList).hasSize(expectedSize);
    }

    @Test
    void getMembersWithWrongStatusCode() throws IOException {
        String memberId = "memberId";
        mockGetMemberCall(memberId, "emptyMembersList.json", 222);

        assertThatThrownBy(() -> graphConnector.getMembers(memberId))
                .isInstanceOf(InvalidGraphResponseException.class)
                .hasMessageContaining("Could not get proper response from Microsoft Graph for the MemberResponseWrapper");
    }

    @Test
    void getGroups() throws IOException {

        mockGetGroupsCall("groups.json", 200);

        List<Group> groupList = graphConnector.getGroups(groupPrefix);

        assertThat(groupList).hasSize(5);
    }

    @Test
    void getOrganizationData() throws IOException {

        mockGetOrganizationDataCall("organizationData.json", 200);

        OrganizationData data = graphConnector.getOrganizationData();
        ObjectMapper mapper = new ObjectMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        OrganizationDataWrapper wrapper = mapper.readValue(getContent("organizationData.json"), OrganizationDataWrapper.class);

        assertThat(data).isEqualTo(wrapper.getValue().get(0));
    }
    @ParameterizedTest
    @MethodSource("testOrganizationValues")
    void getEmptyOrganizationDataResponse(Integer code, Class<?> clazz, String message) throws IOException {

        mockGetOrganizationDataCall("emptyOrganizationData.json", code);

        assertThatThrownBy(graphConnector::getOrganizationData)
                .isInstanceOf(clazz)
                .hasMessageContaining(message);
    }

    @ParameterizedTest
    @MethodSource("testGroupValues")
    void getEmptyGroupsResponse(Integer code, Class<?> clazz, String message) throws IOException {

        mockGetGroupsCall("emptyGroupsList.json", code);

        assertThatThrownBy(() -> graphConnector.getGroups(groupPrefix))
                .isInstanceOf(clazz)
                .hasMessageContaining(message);
    }

    @Test
    void wrongJsonDataResponse() throws IOException {

        String memberId = "memberId";

        mockGetGroupsCall("wrongJsonData.json", 200);
        mockGetOrganizationDataCall("wrongJsonData.json", 200);
        mockGetMemberCall(memberId, "wrongJsonData.json", 200);

        assertThatThrownBy(() -> graphConnector.getGroups(groupPrefix))
                .isInstanceOf(ResponseParsingException.class);
        assertThatThrownBy(graphConnector::getOrganizationData)
                .isInstanceOf(ResponseParsingException.class);
        assertThatThrownBy(() -> graphConnector.getMembers(memberId))
                .isInstanceOf(ResponseParsingException.class);
    }

    private void mockGetGroupsCall(String path, Integer statusCode) throws IOException {
        mockServer.when(
                        request()
                                .withMethod("GET")
                                .withPath("/v1.0/groups")
                                .withQueryStringParameter("$filter", "startswith(displayName, 'test')"),
                        exactly(1)
                )
                .respond(
                        response()
                                .withStatusCode(statusCode)
                                .withHeaders(
                                        new Header("Content-Type", "application/json; charset=utf-8"),
                                        new Header("Cache-Control", "public, max-age=86400"))
                                .withBody(getContent(path))
                                .withDelay(TimeUnit.SECONDS, 1));
    }

    private void mockGetOrganizationDataCall(String path, Integer statusCode) throws IOException {
        mockServer.when(
                        request()
                                .withMethod("GET")
                                .withPath("/v1.0/organization"),
                        exactly(1)
                )
                .respond(
                        response()
                                .withStatusCode(statusCode)
                                .withHeaders(
                                        new Header("Content-Type", "application/json; charset=utf-8"),
                                        new Header("Cache-Control", "public, max-age=86400"))
                                .withBody(getContent(path))
                                .withDelay(TimeUnit.SECONDS, 1));
    }


    private void mockGetMemberCall(String memberId, String path, Integer statusCode) throws IOException {
        mockServer.when(
                        request()
                                .withMethod("GET")
                                .withPath("/v1.0/groups/" + memberId + "/members"),
                        exactly(1)
                )
                .respond(
                        response()
                                .withStatusCode(statusCode)
                                .withHeaders(
                                        new Header("Content-Type", "application/json; charset=utf-8"),
                                        new Header("Cache-Control", "public, max-age=86400"))
                                .withBody(getContent(path))
                                .withDelay(TimeUnit.SECONDS, 1));
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
