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
import ch.sbb.polarion.extension.aad.synchronizer.utils.JsonListParser;
import ch.sbb.polarion.extension.generic.util.JobLogger;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GraphConnector implements IGraphConnector, AutoCloseable {

    private static final String HEADER = "Bearer ";
    private static final String SENT_REQUEST_MESSAGE = "Sent request: %s";
    private static final int JSON_INDENT_FACTOR = 4;
    private static final String GRAPH_MICROSOFT_URL = "https://graph.microsoft.com";

    /**
     * Maximum number of times a single Microsoft Graph request will be retried after receiving a
     * transient throttling/availability response. The total number of attempts is therefore
     * {@code MAX_RETRIES + 1}.
     */
    private static final int MAX_RETRIES = 5;
    private static final long DEFAULT_BACKOFF_BASE_MILLIS = 1_000L;
    private static final long MAX_BACKOFF_MILLIS = 60_000L;
    /**
     * HTTP statuses that Microsoft Graph documents as transient and safe to retry: 429 Too Many
     * Requests (throttling), 503 Service Unavailable, 504 Gateway Timeout. All other non-200 codes
     * are surfaced as {@link InvalidGraphResponseException} on the first occurrence.
     */
    private static final Set<Integer> RETRYABLE_STATUSES = Set.of(429, 503, 504);

    private final IOAuth2SecurityConfiguration authenticationProviderConfiguration;
    private final String token;
    private final String graphUrl;
    private final Map<String, String> graphFieldOverrides;
    private final UrlBuilder urlBuilder;
    private final ObjectMapper objectMapper = prepareObjectMapper();
    /**
     * Monotonically-incremented counter of logical Microsoft Graph operations (one increment per
     * {@link #fetchMSGraphApi} call, regardless of retry attempts). Exposed via
     * {@link #getRequestCount()} for integration tests that need to verify batch-vs-per-user
     * call patterns.
     */
    private int requestCount;
    /**
     * One JAX-RS {@link Client} per connector instance, reused for every Graph request. Avoids the
     * per-call TLS handshake that dominated latency on groups with hundreds of members. The
     * underlying connection pool is released by {@link #close()} at the end of the job.
     */
    private final Client httpClient;

    public GraphConnector(IOAuth2SecurityConfiguration authenticationProviderConfiguration, String token) {
        this(authenticationProviderConfiguration, token, null, GRAPH_MICROSOFT_URL);
    }

    public GraphConnector(IOAuth2SecurityConfiguration authenticationProviderConfiguration, String token,
                          GraphFieldOverrides graphFieldOverrides) {
        this(authenticationProviderConfiguration, token, graphFieldOverrides, GRAPH_MICROSOFT_URL);
    }

    @VisibleForTesting
    GraphConnector(IOAuth2SecurityConfiguration authenticationProviderConfiguration, String token,
                   GraphFieldOverrides graphFieldOverrides, String graphUrl) {
        this.authenticationProviderConfiguration = authenticationProviderConfiguration;
        this.token = token;
        this.graphFieldOverrides = (graphFieldOverrides != null ? graphFieldOverrides : GraphFieldOverrides.EMPTY).asMap();
        this.urlBuilder = new UrlBuilder();
        this.graphUrl = graphUrl;
        this.httpClient = ClientBuilder.newClient();
    }

    @Override
    public int getRequestCount() {
        return requestCount;
    }

    /**
     * Releases the underlying JAX-RS HTTP client and its connection pool. Safe to call multiple
     * times. Should be called once at the end of a synchronization run. Any runtime exception
     * raised by the underlying client is logged and swallowed — a cleanup failure must not mask
     * the actual job result, and the JVM will reclaim the connection pool on shutdown anyway.
     */
    @Override
    public void close() {
        try {
            httpClient.close();
        } catch (RuntimeException e) {
            JobLogger.getInstance().log("Failed to close Graph HTTP client: %s", e.getMessage());
        }
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

    /**
     * Resolves all members of an AAD group through the configured field mapping.
     *
     * <p>For flat standard Graph properties (e.g. {@code employeeId}, {@code displayName},
     * {@code mail}) a single batch call {@code /groups/{id}/members?$select=...} returns every
     * member's projected fields inline, giving {@code 1 + ceil(N / pageSize)} HTTPS calls per
     * group instead of {@code 1 + N}.</p>
     *
     * <p>When any resolved field is a directory schema extension (prefix {@code extension_}) or
     * a nested property path (contains {@code /}), the connector falls back to a per-user fetch
     * via {@code /users/{aadObjectId}}. Background: issue #74 reported that {@code $select} on
     * {@code /groups/{id}/members} silently dropped extension attributes on certain tenant
     * configurations. Per-user fetch is the safe path for that case; the cost is {@code N}
     * extra HTTPS calls, mitigated by the shared {@link Client} connection pool and by
     * retry/backoff handling on 429/503/504 in {@link #fetchMSGraphApi}.</p>
     */
    @Override
    public List<Member> getMembers(String key) {
        String idField = resolveGraphField(MemberResponseWrapper.ID, authenticationProviderConfiguration.mapping().id());
        String nameField = resolveGraphField(MemberResponseWrapper.NAME, authenticationProviderConfiguration.mapping().name());
        String emailField = resolveGraphField(MemberResponseWrapper.EMAIL, authenticationProviderConfiguration.mapping().email());

        Map<String, String> userFieldsMapping = Map.ofEntries(
                Map.entry(MemberResponseWrapper.ID, idField),
                Map.entry(MemberResponseWrapper.NAME, nameField),
                Map.entry(MemberResponseWrapper.EMAIL, emailField)
        );
        String selectValue = "%s,%s,%s".formatted(idField, nameField, emailField);

        if (requiresPerUserFetch(idField, nameField, emailField)) {
            return fetchMembersPerUser(key, selectValue, userFieldsMapping);
        }
        return fetchMembersBatch(key, selectValue, userFieldsMapping);
    }

    /**
     * True when any resolved Graph property name requires the per-user fallback path: directory
     * schema extensions (prefix {@code extension_}) or nested property paths (contain {@code /}).
     * See the {@link #getMembers} javadoc for the rationale.
     */
    @VisibleForTesting
    static boolean requiresPerUserFetch(String... fields) {
        for (String f : fields) {
            if (f != null && (f.startsWith("extension_") || f.contains("/"))) {
                return true;
            }
        }
        return false;
    }

    private List<Member> fetchMembersPerUser(String groupKey, String selectValue, Map<String, String> userFieldsMapping) {
        List<String> aadObjectIds = fetchGroupMemberObjectIds(groupKey);
        List<Member> members = new ArrayList<>(aadObjectIds.size());
        for (String aadObjectId : aadObjectIds) {
            members.add(fetchUser(aadObjectId, selectValue, userFieldsMapping));
        }
        return members;
    }

    private List<Member> fetchMembersBatch(String groupKey, String selectValue, Map<String, String> userFieldsMapping) {
        String url = urlBuilder.build(graphUrl, GraphOption.GROUPS, String.format("%s/members", groupKey));
        List<Member> members = new ArrayList<>();
        String body = fetchMSGraphApi(url, "$select", selectValue, String.class);
        String nextLink = collectUserMembers(body, members, userFieldsMapping);
        while (nextLink != null) {
            body = fetchMSGraphApi(nextLink, null, null, String.class);
            nextLink = collectUserMembers(body, members, userFieldsMapping);
        }
        return members;
    }

    /**
     * Walks one page of a {@code /groups/{id}/members} response, building a {@link Member} for
     * each {@code @odata.type == #microsoft.graph.user} entry. Non-user directory objects
     * (nested groups, service principals, devices, organizational contacts) are skipped — the
     * same filter as {@link #collectUserMemberIds}, applied here against the already-projected
     * user fields instead of bare ids.
     *
     * @return the {@code @odata.nextLink} for cursor-based pagination, or {@code null}.
     */
    private String collectUserMembers(String body, List<Member> sink, Map<String, String> fieldsMapping) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode value = root.get(JsonListParser.VALUE);
            if (value != null && value.isArray()) {
                for (JsonNode item : value) {
                    JsonNode type = item.get("@odata.type");
                    if (type == null || !GRAPH_USER_TYPE.equals(type.asText())) {
                        continue;
                    }
                    sink.add(parseMember(item, fieldsMapping));
                }
            }
            JsonNode nextLink = root.get(JsonListParser.NEXT_LINK);
            return (nextLink != null && !nextLink.isNull()) ? nextLink.asText() : null;
        } catch (JsonProcessingException e) {
            throw new ResponseParsingException(e);
        }
    }

    private static Member parseMember(JsonNode item, Map<String, String> fieldsMapping) {
        Member m = new Member();
        m.setId(readTextField(item, fieldsMapping.get(MemberResponseWrapper.ID)));
        m.setName(readTextField(item, fieldsMapping.get(MemberResponseWrapper.NAME)));
        m.setEmail(readTextField(item, fieldsMapping.get(MemberResponseWrapper.EMAIL)));
        return m;
    }

    private static String readTextField(JsonNode node, String fieldName) {
        if (fieldName == null) {
            return null;
        }
        JsonNode field = node.get(fieldName);
        return (field == null || field.isNull()) ? null : field.asText();
    }

    private static final String GRAPH_USER_TYPE = "#microsoft.graph.user";

    private List<String> fetchGroupMemberObjectIds(String key) {
        String url = urlBuilder.build(graphUrl, GraphOption.GROUPS, String.format("%s/members", key));
        String body = fetchMSGraphApi(url, "$select", "id", String.class);

        List<String> ids = new ArrayList<>();
        String nextLink = collectUserMemberIds(body, ids);
        while (nextLink != null) {
            body = fetchMSGraphApi(nextLink, null, null, String.class);
            nextLink = collectUserMemberIds(body, ids);
        }
        return ids;
    }

    /**
     * Walks one page of a {@code /groups/{id}/members} response, collecting object ids of
     * members whose {@code @odata.type} is {@value #GRAPH_USER_TYPE}. Members of other directory
     * object types (nested groups, service principals, devices, organizational contacts) are
     * skipped — propagating their ids would cause {@link #fetchUser(String, String, java.util.Map)}
     * to hit {@code /users/{id}} and get 404, blowing up the entire synchronization job.
     *
     * @return the {@code @odata.nextLink} for cursor-based pagination, or {@code null} when there
     *         are no more pages.
     */
    private String collectUserMemberIds(String body, List<String> sink) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode value = root.get(JsonListParser.VALUE);
            if (value != null && value.isArray()) {
                for (JsonNode item : value) {
                    JsonNode type = item.get("@odata.type");
                    if (type == null || !GRAPH_USER_TYPE.equals(type.asText())) {
                        continue;
                    }
                    JsonNode id = item.get("id");
                    if (id != null && !id.isNull()) {
                        sink.add(id.asText());
                    }
                }
            }
            JsonNode nextLink = root.get(JsonListParser.NEXT_LINK);
            return (nextLink != null && !nextLink.isNull()) ? nextLink.asText() : null;
        } catch (JsonProcessingException e) {
            throw new ResponseParsingException(e);
        }
    }

    private Member fetchUser(String aadObjectId, String selectValue, Map<String, String> fieldsMapping) {
        String url = urlBuilder.build(graphUrl, GraphOption.USERS, aadObjectId);
        String body = fetchMSGraphApi(url, "$select", selectValue, String.class);
        return JsonListParser.parseObject(body, fieldsMapping, Member.class);
    }

    /**
     * Resolves the Microsoft Graph property name to request for the given mapping field role.
     * When a per-field override was supplied via job parameters, it wins unconditionally and is
     * passed to Graph verbatim. Otherwise falls back to the value from
     * {@code authentication.xml} {@code <mapping>}.
     */
    @VisibleForTesting
    String resolveGraphField(String fieldKey, String mappingValue) {
        return graphFieldOverrides.getOrDefault(fieldKey, mappingValue);
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

    private <T> T fetchMSGraphApi(String url, String queryParamName, String queryParamValue, Class<T> targetClass) {
        requestCount++;
        WebTarget webTarget = httpClient.target(url);
        if (queryParamName != null && queryParamValue != null) {
            webTarget = webTarget.queryParam(queryParamName, queryParamValue);
        }

        int attempt = 0;
        while (true) {
            try (Response response = webTarget.request(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, HEADER + token)
                    .get()) {

                JobLogger.getInstance().log(SENT_REQUEST_MESSAGE, webTarget.getUri());
                JobLogger.getInstance().log(getResponseStatusLine(response));

                int status = response.getStatus();
                if (status == Response.Status.OK.getStatusCode()) {
                    return parseSuccessfulResponse(response, targetClass);
                }
                if (shouldRetry(status, attempt)) {
                    waitBeforeRetry(response, status, attempt);
                    attempt++;
                    continue;
                }
                throw mapErrorStatus(status);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T parseSuccessfulResponse(Response response, Class<T> targetClass) {
        String responseContent = getResponseContent(response);
        if (targetClass == String.class) {
            return (T) responseContent;
        }
        try {
            return objectMapper.readValue(responseContent, targetClass);
        } catch (JsonProcessingException e) {
            throw new ResponseParsingException(e);
        }
    }

    private static boolean shouldRetry(int status, int attempt) {
        return RETRYABLE_STATUSES.contains(status) && attempt < MAX_RETRIES;
    }

    private static void waitBeforeRetry(Response response, int status, int attempt) {
        long sleepMillis = computeBackoffMillis(response, attempt);
        JobLogger.getInstance().log(
                "Got HTTP %d from Microsoft Graph; retrying in %d ms (attempt %d/%d)",
                status, sleepMillis, attempt + 1, MAX_RETRIES);
        sleepInterruptibly(sleepMillis);
    }

    private static InvalidGraphResponseException mapErrorStatus(int status) {
        if (status == Response.Status.UNAUTHORIZED.getStatusCode()) {
            return new InvalidGraphResponseException("Microsoft Graph token expired");
        }
        return new InvalidGraphResponseException("Could not get proper response from Microsoft Graph");
    }

    /**
     * Computes how long to wait before retrying a throttled/unavailable Graph response. Honours
     * the {@code Retry-After} header when present and parsable as an integer number of seconds
     * (the format Microsoft Graph uses for throttling). Otherwise falls back to exponential
     * backoff: 1s, 2s, 4s, 8s, 16s — capped at {@value #MAX_BACKOFF_MILLIS} ms.
     */
    @VisibleForTesting
    static long computeBackoffMillis(Response response, int attempt) {
        String retryAfter = response.getHeaderString(HttpHeaders.RETRY_AFTER);
        if (retryAfter != null) {
            try {
                long seconds = Long.parseLong(retryAfter.trim());
                if (seconds >= 0) {
                    return Math.min(seconds * 1_000L, MAX_BACKOFF_MILLIS);
                }
            } catch (NumberFormatException ignored) {
                // Retry-After can also be an HTTP-date; we don't parse that, fall through to
                // exponential backoff so the retry still happens.
            }
        }
        long backoff = DEFAULT_BACKOFF_BASE_MILLIS << Math.min(attempt, 10);
        return Math.min(backoff, MAX_BACKOFF_MILLIS);
    }

    private static void sleepInterruptibly(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InvalidGraphResponseException("Interrupted while waiting to retry Microsoft Graph request");
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
