package ch.sbb.polarion.extension.aad.synchronizer.connector;

import ch.sbb.polarion.extension.aad.synchronizer.exception.InvalidGraphResponseException;
import ch.sbb.polarion.extension.aad.synchronizer.exception.NotFoundException;
import ch.sbb.polarion.extension.aad.synchronizer.exception.ResponseParsingException;
import ch.sbb.polarion.extension.aad.synchronizer.model.Group;
import ch.sbb.polarion.extension.aad.synchronizer.model.GroupResponseWrapper;
import ch.sbb.polarion.extension.aad.synchronizer.model.Member;
import ch.sbb.polarion.extension.aad.synchronizer.model.MemberResponseWrapper;
import ch.sbb.polarion.extension.aad.synchronizer.model.OrganizationData;
import ch.sbb.polarion.extension.aad.synchronizer.model.OrganizationDataWrapper;
import ch.sbb.polarion.extension.generic.util.JobLogger;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polarion.core.config.IOAuth2SecurityConfiguration;
import org.jetbrains.annotations.VisibleForTesting;
import org.json.JSONException;
import org.json.JSONObject;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

public class GraphConnector implements IGraphConnector {

    private static final String HEADER = "Bearer ";
    private static final String SENT_REQUEST_MESSAGE = "Sent request: %s";
    private static final int JSON_INDENT_FACTOR = 4;
    private static final String GRAPH_MICROSOFT_URL = "https://graph.microsoft.com";

    private final IOAuth2SecurityConfiguration authenticationProviderConfiguration;
    private final String token;
    private final String graphUrl;
    private final UrlBuilder urlBuilder;
    private final ObjectMapper objectMapper = prepareObjectMapper();

    public GraphConnector(IOAuth2SecurityConfiguration authenticationProviderConfiguration, String token) {
        this(authenticationProviderConfiguration, token, GRAPH_MICROSOFT_URL);
    }

    @VisibleForTesting
    GraphConnector(IOAuth2SecurityConfiguration authenticationProviderConfiguration, String token, String graphUrl) {
        this.authenticationProviderConfiguration = authenticationProviderConfiguration;
        this.token = token;
        this.urlBuilder = new UrlBuilder();
        this.graphUrl = graphUrl;
    }

    private ObjectMapper prepareObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return mapper;
    }

    @Override
    public List<Group> getGroups(String groupPrefix) {
        String url = urlBuilder.build(graphUrl, GraphOption.GROUPS);
        String filterValue = "startswith(displayName, '" + groupPrefix + "')";
        GroupResponseWrapper searchResult = fetchMSGraphApi(url, "$filter", filterValue, GroupResponseWrapper.class);
        if (searchResult.getValue() == null || searchResult.getValue().isEmpty()) {
            throw new NotFoundException("No AAD groups were found.");
        }
        return searchResult.getValue();
    }

    @Override
    public List<Member> getMembers(String key) {
        String id = authenticationProviderConfiguration.mapping().id();
        String name = authenticationProviderConfiguration.mapping().name();
        String email = authenticationProviderConfiguration.mapping().email();

        Map<String, String> fieldsMapping = Map.ofEntries(
                Map.entry(MemberResponseWrapper.ID, id),
                Map.entry(MemberResponseWrapper.NAME, name),
                Map.entry(MemberResponseWrapper.EMAIL, email)
        );

        String searchEndpoint = String.format("%s/members", key);
        String url = urlBuilder.build(graphUrl, GraphOption.GROUPS, searchEndpoint);
        String selectValue = "%s,%s,%s".formatted(id, name, email);
        String searchResult = fetchMSGraphApi(url, "$select", selectValue, String.class);
        MemberResponseWrapper memberResponseWrapper = MemberResponseWrapper.fromJsonList(searchResult, fieldsMapping);
        return memberResponseWrapper.getValue();
    }

    @Override
    public OrganizationData getOrganizationData() {
        String url = urlBuilder.build(graphUrl, GraphOption.ORGANIZATION);
        OrganizationDataWrapper searchResult = fetchMSGraphApi(url, null, null, OrganizationDataWrapper.class);
        if (searchResult.getValue().isEmpty()) {
            throw new NotFoundException("No Organization data was found.");
        }
        return searchResult.getValue().get(0);
    }

    @SuppressWarnings("unchecked")
    private <T> T fetchMSGraphApi(String url, String queryParamName, String queryParamValue, Class<T> targetClass) {
        Client client = null;
        try {
            client = ClientBuilder.newClient();
            WebTarget webTarget = client.target(url);
            if (queryParamName != null && queryParamValue != null) {
                webTarget = webTarget.queryParam(queryParamName, queryParamValue);
            }

            try (Response response = webTarget.request(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, HEADER + token)
                    .get()) {

                JobLogger.getInstance().log(SENT_REQUEST_MESSAGE, webTarget.getUri());
                JobLogger.getInstance().log(getResponseStatusLine(response));

                if (response.getStatus() == Response.Status.OK.getStatusCode()) {
                    String responseContent = getResponseContent(response);
                    if (targetClass == String.class) {
                        return (T) responseContent;
                    }
                    try {
                        return objectMapper.readValue(responseContent, targetClass);
                    } catch (JsonProcessingException e) {
                        throw new ResponseParsingException(e);
                    }
                } else {
                    if (response.getStatus() == Response.Status.UNAUTHORIZED.getStatusCode()) {
                        throw new InvalidGraphResponseException("Microsoft Graph token expired");
                    } else {
                        throw new InvalidGraphResponseException("Could not get proper response from Microsoft Graph");
                    }
                }
            }
        } finally {
            if (client != null) {
                client.close();
            }
        }
    }

    private static String getResponseContent(Response response) {
        String responseContent = response.readEntity(String.class);
        String jsonContent = null;
        try {
            jsonContent = new JSONObject(responseContent).toString(JSON_INDENT_FACTOR);
        } catch (JSONException e) {
            JobLogger.getInstance().log("Cannot parse JSON response: %s", e.getMessage());
        }
        JobLogger.getInstance().log("Response content: %s%s", System.lineSeparator(), jsonContent == null ? responseContent : jsonContent);
        return responseContent;
    }

    private String getResponseStatusLine(Response response) {
        return "Response status: " + response.getStatus() + " " + Response.Status.fromStatusCode(response.getStatus());
    }
}
