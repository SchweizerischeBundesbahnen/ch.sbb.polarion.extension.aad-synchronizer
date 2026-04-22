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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final String extensionAppId;
    private final Set<String> extensionFields;
    private final Map<String, String> graphFieldOverrides;
    private final UrlBuilder urlBuilder;
    private final ObjectMapper objectMapper = prepareObjectMapper();
    /**
     * One JAX-RS {@link Client} per connector instance, reused for every Graph request. Avoids the
     * per-call TLS handshake that dominated latency on groups with hundreds of members. The
     * underlying connection pool is released by {@link #close()} at the end of the job.
     */
    private final Client httpClient;

    public GraphConnector(IOAuth2SecurityConfiguration authenticationProviderConfiguration, String token) {
        this(authenticationProviderConfiguration, token, null, null, null, GRAPH_MICROSOFT_URL);
    }

    public GraphConnector(IOAuth2SecurityConfiguration authenticationProviderConfiguration, String token,
                          String extensionAppId, String extensionFields,
                          GraphFieldOverrides graphFieldOverrides) {
        this(authenticationProviderConfiguration, token, extensionAppId, extensionFields, graphFieldOverrides, GRAPH_MICROSOFT_URL);
    }

    @VisibleForTesting
    GraphConnector(IOAuth2SecurityConfiguration authenticationProviderConfiguration, String token,
                   String extensionAppId, String extensionFields,
                   GraphFieldOverrides graphFieldOverrides, String graphUrl) {
        this.authenticationProviderConfiguration = authenticationProviderConfiguration;
        this.token = token;
        this.extensionAppId = extensionAppId;
        this.extensionFields = parseExtensionFields(extensionFields, extensionAppId);
        this.graphFieldOverrides = (graphFieldOverrides != null ? graphFieldOverrides : GraphFieldOverrides.EMPTY).asMap();
        this.urlBuilder = new UrlBuilder();
        this.graphUrl = graphUrl;
        this.httpClient = ClientBuilder.newClient();
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

    /**
     * Parses the comma-separated {@code extensionFields} job parameter into a set of mapping field
     * keys ({@code id}, {@code name}, {@code email}). When unset but {@code extensionAppId} is
     * configured, defaults to {@code {id}} for backward compatibility — the original use case being
     * a custom identifier attribute.
     */
    private static Set<String> parseExtensionFields(String csv, String extensionAppId) {
        if (csv == null || csv.isBlank()) {
            return (extensionAppId == null || extensionAppId.isBlank())
                    ? Set.of()
                    : Set.of(MemberResponseWrapper.ID);
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
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
     * <p><b>Why two requests per member instead of one batch call</b>: the synchronizer used to
     * read members in a single {@code /groups/{id}/members?$select=...} call, but that endpoint
     * returns {@code directoryObject}s and {@code $select} silently drops directory schema
     * extension properties on certain configurations — see issue #74. Switching to a per-user
     * fetch via {@code /users/{aadObjectId}} guarantees the extension attributes come back
     * populated and isolates the synchronizer from any future {@code /members} quirks (nested
     * directory object types, mixed-tenant guests, multi-valued extensions). The cost is an
     * extra {@code N} HTTPS calls per group: tolerable for the daily cron, mitigated by
     * connection reuse via the per-connector {@link Client} and by retry/backoff handling on
     * 429/503/504 in {@link #fetchMSGraphApi}.</p>
     */
    @Override
    public List<Member> getMembers(String key) {
        String idField = resolveGraphField(MemberResponseWrapper.ID, authenticationProviderConfiguration.mapping().id());
        String nameField = resolveGraphField(MemberResponseWrapper.NAME, authenticationProviderConfiguration.mapping().name());
        String emailField = resolveGraphField(MemberResponseWrapper.EMAIL, authenticationProviderConfiguration.mapping().email());

        List<String> aadObjectIds = fetchGroupMemberObjectIds(key);

        Map<String, String> userFieldsMapping = Map.ofEntries(
                Map.entry(MemberResponseWrapper.ID, idField),
                Map.entry(MemberResponseWrapper.NAME, nameField),
                Map.entry(MemberResponseWrapper.EMAIL, emailField)
        );
        String selectValue = "%s,%s,%s".formatted(idField, nameField, emailField);

        List<Member> members = new ArrayList<>(aadObjectIds.size());
        for (String aadObjectId : aadObjectIds) {
            members.add(fetchUser(aadObjectId, selectValue, userFieldsMapping));
        }
        return members;
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
     * passed to Graph verbatim (no {@code extension_...} expansion applied). Otherwise falls back
     * to the value from {@code authentication.xml} {@code <mapping>}, with the legacy
     * {@code extensionAppId}/{@code extensionFields} auto-expansion applied for backward
     * compatibility.
     */
    @VisibleForTesting
    String resolveGraphField(String fieldKey, String mappingValue) {
        String override = graphFieldOverrides.get(fieldKey);
        if (override != null) {
            return override;
        }
        return expandIfMarked(fieldKey, mappingValue);
    }

    /**
     * Returns the configured value, expanded to a directory schema extension property name when the
     * given mapping field key (one of {@code id}, {@code name}, {@code email}) is listed in
     * {@code extensionFields}. When the field is not marked as an extension, the value is used as-is.
     */
    @VisibleForTesting
    String expandIfMarked(String fieldKey, String fieldValue) {
        if (!extensionFields.contains(fieldKey)) {
            return fieldValue;
        }
        return expandExtensionAttribute(fieldValue);
    }

    /**
     * If {@code extensionAppId} is configured, expands a bare custom attribute name into the
     * fully-qualified Microsoft Graph directory schema extension property name
     * {@code extension_<appIdWithoutDashes>_<name>}. Leaves the value untouched when:
     * <ul>
     *     <li>no {@code extensionAppId} is configured;</li>
     *     <li>the value already starts with {@code extension_} (already a full extension name);</li>
     *     <li>the value contains a slash (a nested property path such as
     *         {@code onPremisesExtensionAttributes/extensionAttribute1}).</li>
     * </ul>
     */
    @VisibleForTesting
    String expandExtensionAttribute(String fieldName) {
        if (extensionAppId == null || extensionAppId.isBlank()
                || fieldName == null
                || fieldName.startsWith("extension_")
                || fieldName.contains("/")) {
            return fieldName;
        }
        return "extension_" + extensionAppId.replace("-", "") + "_" + fieldName;
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
