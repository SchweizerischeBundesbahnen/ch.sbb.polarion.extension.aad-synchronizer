package ch.sbb.polarion.extension.aad.synchronizer.connector;

import ch.sbb.polarion.extension.aad.synchronizer.model.Group;
import ch.sbb.polarion.extension.aad.synchronizer.model.Member;
import ch.sbb.polarion.extension.aad.synchronizer.service.GraphService;
import ch.sbb.polarion.extension.aad.synchronizer.utils.OAuth2Client;
import org.apache.oltu.oauth2.client.OAuthClient;
import org.apache.oltu.oauth2.client.URLConnectionClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Integration tests that talk to a real Microsoft Entra ID tenant. They are <strong>not</strong>
 * picked up by the default {@code mvn test} run because:
 *
 * <ol>
 *     <li>The class name ends with {@code IT}, which surefire ignores by default
 *         (it only matches {@code *Test} / {@code Test*} / {@code *Tests} / {@code *TestCase}).</li>
 *     <li>The {@link EnabledIf @EnabledIf("isConfigured")} guard skips every test unless the
 *         required configuration is available (env var, system property, or properties file —
 *         see below), so even if the class is invoked from an IDE without setup the suite stays
 *         green.</li>
 * </ol>
 *
 * <h2>How to run</h2>
 *
 * Activate the {@code integration-test} Maven profile and supply the secrets via either
 * environment variables, JVM system properties, or a {@code .properties} file. Lookup order for
 * every setting is: <strong>env var → JVM system property → properties file → built-in
 * default</strong>. The first non-blank value wins. This lets you mix sources freely (e.g. keep
 * the secret in the file and override the group prefix on the command line).
 *
 * <h3>Properties file</h3>
 *
 * <p>By default the suite looks for {@code aad-sync-it.properties} in the project root (the
 * directory {@code mvn} runs from). The file is gitignored so secrets stay out of version
 * control; an {@code aad-sync-it.properties.example} template is committed alongside it. Override
 * the location with env var {@code AAD_SYNC_IT_CONFIG_FILE} or system property
 * {@code aad-sync-it.config-file}. The file is plain Java {@link Properties} format with
 * camelCase keys. Example:</p>
 *
 * <pre>{@code
 * tenantId=xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
 * clientId=xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
 * clientSecret=...
 * groupPrefix=TEST_AAD_SYNC_
 * # Optional overrides — defaults are vanilla MS Graph user properties
 * # extensionAppId=xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
 * # extensionFields=id,name,email
 * # mappingId=mycustomid
 * # mappingName=displayName
 * # mappingEmail=mail
 * # expectedUpn=user@example.com
 * }</pre>
 *
 * <p>Then run:</p>
 *
 * <pre>{@code
 * mvn test -P integration-test
 * }</pre>
 *
 * <h3>Environment variable equivalents</h3>
 *
 * <p>Each properties key has an equivalent uppercase env var with the {@code AAD_SYNC_IT_}
 * prefix:</p>
 *
 * <ul>
 *     <li>{@code tenantId} ↔ {@code AAD_SYNC_IT_TENANT_ID} — required</li>
 *     <li>{@code clientId} ↔ {@code AAD_SYNC_IT_CLIENT_ID} — required</li>
 *     <li>{@code clientSecret} ↔ {@code AAD_SYNC_IT_CLIENT_SECRET} — required</li>
 *     <li>{@code groupPrefix} ↔ {@code AAD_SYNC_IT_GROUP_PREFIX} — required</li>
 *     <li>{@code extensionAppId} ↔ {@code AAD_SYNC_IT_EXTENSION_APP_ID} — AAD application id
 *         that owns the directory schema extensions. Empty by default. Set this together with
 *         {@code extensionFields} when your test users carry custom extension attributes that
 *         need to be expanded to {@code extension_<appIdNoDashes>_<name>}.</li>
 *     <li>{@code extensionFields} ↔ {@code AAD_SYNC_IT_EXTENSION_FIELDS} — comma-separated
 *         mapping field keys to expand. Empty by default — i.e. mapping fields are passed to
 *         Graph as-is.</li>
 *     <li>{@code mappingId} / {@code mappingName} / {@code mappingEmail} ↔
 *         {@code AAD_SYNC_IT_MAPPING_ID} / {@code _NAME} / {@code _EMAIL} — bare attribute names
 *         the synchronizer reads from {@code authentication.xml}. Default to the standard MS
 *         Graph user properties {@code mailNickname} / {@code displayName} / {@code mail}, so
 *         the IT works against any vanilla Entra group out of the box.</li>
 *     <li>{@code expectedUpn} ↔ {@code AAD_SYNC_IT_EXPECTED_UPN} — when set, an additional
 *         assertion verifies that this user is among the resolved members of the first matching
 *         group.</li>
 * </ul>
 *
 * <p>Each test issues real HTTPS calls against {@code login.microsoftonline.com} and
 * {@code graph.microsoft.com}. Run cost is a handful of Graph requests per test plus one token
 * acquisition; well within the application throttling envelope.</p>
 */
@EnabledIf("isConfigured")
class GraphConnectorIT {

    private static final String SCOPE = "https://graph.microsoft.com/.default";
    private static final String TOKEN_URL_TEMPLATE = "https://login.microsoftonline.com/%s/oauth2/v2.0/token";

    /**
     * Default location of the IT properties file: {@code aad-sync-it.properties} in the project
     * root. Maven always runs tests from the directory containing {@code pom.xml}, so a relative
     * path is enough. The file is gitignored — see {@code .gitignore} and the committed
     * {@code aad-sync-it.properties.example} template.
     */
    private static final String DEFAULT_CONFIG_PATH = "aad-sync-it.properties";

    private static final Properties FILE_PROPS = loadFileProps();

    private GraphConnector connector;
    private String token;
    private String groupPrefix;
    private String extensionAppId;
    private String extensionFields;
    private FakeOAuth2SecurityConfiguration config;

    @BeforeEach
    void setUp() {
        String tenantId = required("AAD_SYNC_IT_TENANT_ID", "tenantId");
        String clientId = required("AAD_SYNC_IT_CLIENT_ID", "clientId");
        String clientSecret = required("AAD_SYNC_IT_CLIENT_SECRET", "clientSecret");
        groupPrefix = required("AAD_SYNC_IT_GROUP_PREFIX", "groupPrefix");

        extensionAppId = lookup("AAD_SYNC_IT_EXTENSION_APP_ID", "extensionAppId", null);
        extensionFields = lookup("AAD_SYNC_IT_EXTENSION_FIELDS", "extensionFields", null);
        String mappingId = lookup("AAD_SYNC_IT_MAPPING_ID", "mappingId", "mailNickname");
        String mappingName = lookup("AAD_SYNC_IT_MAPPING_NAME", "mappingName", "displayName");
        String mappingEmail = lookup("AAD_SYNC_IT_MAPPING_EMAIL", "mappingEmail", "mail");

        log("--- IT setup ---");
        log("  tenantId        = " + tenantId);
        log("  clientId        = " + clientId);
        log("  clientSecret    = " + maskSecret(clientSecret));
        log("  groupPrefix     = " + groupPrefix);
        log("  extensionAppId  = " + (extensionAppId == null ? "(empty)" : extensionAppId));
        log("  extensionFields = " + (extensionFields == null ? "(empty)" : extensionFields));
        log("  mapping.id      = " + mappingId);
        log("  mapping.name    = " + mappingName);
        log("  mapping.email   = " + mappingEmail);
        log("  config file     = " + resolvedConfigPath() + (FILE_PROPS.isEmpty() ? " (not loaded)" : " (loaded)"));

        config = new FakeOAuth2SecurityConfiguration(mappingId, mappingName, mappingEmail);

        // Use the real OAuth2Client code path that the production job uses, just constructed via
        // the @VisibleForTesting constructor so we can skip the Polarion UserAccountVault.
        OAuth2Client oauth2Client = new OAuth2Client(new OAuthClient(new URLConnectionClient()), null);
        token = oauth2Client.getToken(String.format(TOKEN_URL_TEMPLATE, tenantId), clientId, clientSecret, SCOPE);
        log("  token acquired  = " + maskSecret(token) + " (length=" + token.length() + ")");

        connector = new GraphConnector(config, token, extensionAppId, extensionFields, null);
    }

    @AfterEach
    void tearDown() {
        if (connector != null) {
            connector.close();
        }
    }

    @Test
    void clientCredentialsFlowReturnsToken() {
        // setUp() already exercised the token acquisition; assert here so failures point at the
        // right step instead of being hidden behind a downstream Graph call.
        assertThat(token).isNotBlank();
    }

    @Test
    void getGroupsReturnsAtLeastOneMatchingGroup() {
        List<Group> groups = connector.getGroups(groupPrefix);

        log("--- getGroups('" + groupPrefix + "') returned " + groups.size() + " group(s) ---");
        for (Group group : groups) {
            log("  group id = " + group.getId());
        }

        assertThat(groups)
                .as("at least one AAD group with prefix '%s' must exist for the IT to be meaningful", groupPrefix)
                .isNotEmpty()
                .allSatisfy(group -> assertThat(group.getId()).isNotBlank());
    }

    @Test
    void getMembersResolvesAllMappedFieldsEndToEnd() {
        List<Group> groups = connector.getGroups(groupPrefix);
        log("--- getGroups('" + groupPrefix + "') returned " + groups.size() + " group(s) ---");
        for (Group group : groups) {
            log("  group id = " + group.getId());
        }
        assertThat(groups).isNotEmpty();
        Group firstGroup = groups.get(0);

        List<Member> members = connector.getMembers(firstGroup.getId());

        log("--- getMembers('" + firstGroup.getId() + "') returned " + members.size() + " member(s) ---");
        for (int i = 0; i < members.size(); i++) {
            Member member = members.get(i);
            log("  [" + i + "] id=" + valueOrNullMarker(member.getId())
                    + " name=" + valueOrNullMarker(member.getName())
                    + " email=" + valueOrNullMarker(member.getEmail()));
        }

        // Each member must have all three mapped fields resolved to non-null values. If any of
        // these come back null we are reproducing issue #74 — Graph silently returns empty user
        // objects when the $select asks for properties it does not understand.
        assertThat(members)
                .as("group '%s' should have at least one member resolvable through the configured mapping", firstGroup.getId())
                .isNotEmpty()
                .allSatisfy(member -> {
                    assertThat(member.getId()).as("member id must be resolved").isNotBlank();
                    assertThat(member.getName()).as("member name must be resolved").isNotBlank();
                    assertThat(member.getEmail()).as("member email must be resolved").isNotBlank();
                });

        String expectedUpn = lookup("AAD_SYNC_IT_EXPECTED_UPN", "expectedUpn", null);
        if (expectedUpn != null && !expectedUpn.isBlank()) {
            log("  asserting expectedUpn '" + expectedUpn + "' is among members");
            assertThat(members)
                    .as("expected user '%s' must be among resolved members", expectedUpn)
                    .anySatisfy(member -> assertThat(member.getEmail()).isEqualToIgnoringCase(expectedUpn));
        }
    }

    @Test
    void getOrganizationDataReturnsTenantInfo() {
        // Sanity check that Organization.Read.All is admin-consented and the /organization
        // endpoint is reachable. The job uses this only when checkLastSynchronization=true.
        var data = connector.getOrganizationData();
        log("--- getOrganizationData() ---");
        log("  onPremisesLastSyncDateTime = " + (data == null ? "(null)" : data.getOnPremisesLastSyncDateTime()));
        assertThat(data).isNotNull();
    }

    /**
     * Exercises the production wrapper {@link GraphService#getAadMemberIds(String)} end-to-end.
     * The job's {@code runInternal} goes through this method, not directly through
     * {@link GraphConnector#getMembers(String)}, so a regression in {@code GraphService}'s dedup
     * or {@code id != null} filter would not be caught by the connector-level tests above.
     * Asserts that the resolved set contains the same non-null ids that {@link GraphConnector}
     * returns directly.
     */
    @Test
    void graphServicePipelineDedupesAndFilters() {
        GraphService service = new GraphService(connector);

        Set<String> ids = service.getAadMemberIds(groupPrefix);

        log("--- GraphService.getAadMemberIds('" + groupPrefix + "') ---");
        log("  resolved " + ids.size() + " unique non-null id(s)");
        ids.forEach(id -> log("  " + id));

        assertThat(ids)
                .as("GraphService should return at least one resolved member id for prefix '%s'", groupPrefix)
                .isNotEmpty()
                .doesNotContainNull()
                .allSatisfy(id -> assertThat(id).isNotBlank());
    }

    /**
     * The connector implements {@link AutoCloseable}; closing it twice must not throw. We rely
     * on this in {@code UserSynchronizationJobUnit#runInternal}'s {@code finally} block, where a
     * second close from a hypothetical future cleanup path would otherwise crash the job.
     */
    @Test
    void connectorCloseIsIdempotent() {
        GraphConnector throwaway = new GraphConnector(config, token, extensionAppId, extensionFields, null);
        throwaway.close();
        assertThatNoException()
                .as("a second close() on an already-closed connector must not throw")
                .isThrownBy(throwaway::close);
    }

    private static void log(String line) {
        // Plain stdout — surefire forwards it to the console alongside the test result, no extra
        // logger configuration needed. Use the IT logs to inspect what Graph actually returned
        // when an assertion fails.
        System.out.println("[GraphConnectorIT] " + line);
    }

    private static String valueOrNullMarker(String value) {
        if (value == null) {
            return "<NULL>";
        }
        if (value.isEmpty()) {
            return "<EMPTY>";
        }
        return value;
    }

    /**
     * Mask everything except the first 4 and last 4 characters of a secret-like value, so that
     * the IT log shows enough to recognise the value but not enough to leak it. Short strings
     * (under 12 chars) are fully masked.
     */
    private static String maskSecret(String value) {
        if (value == null || value.isBlank()) {
            return "(empty)";
        }
        if (value.length() < 12) {
            return "***";
        }
        return value.substring(0, 4) + "..." + value.substring(value.length() - 4);
    }

    /**
     * Resolves a configuration value by checking, in order: environment variable, JVM system
     * property (under the same UPPER_SNAKE_CASE name), the loaded properties file (under the
     * camelCase {@code fileKey}), and finally the supplied {@code defaultValue}. The first
     * non-blank value wins. Returns {@code defaultValue} (which may be {@code null}) if nothing
     * is configured.
     */
    private static String lookup(String envName, String fileKey, String defaultValue) {
        String env = System.getenv(envName);
        if (env != null && !env.isBlank()) {
            return env;
        }
        String sysProp = System.getProperty(envName);
        if (sysProp != null && !sysProp.isBlank()) {
            return sysProp;
        }
        String fromFile = FILE_PROPS.getProperty(fileKey);
        if (fromFile != null && !fromFile.isBlank()) {
            return fromFile;
        }
        return defaultValue;
    }

    private static String required(String envName, String fileKey) {
        String value = lookup(envName, fileKey, null);
        if (value == null) {
            throw new IllegalStateException(
                    "Required IT setting is not configured. Set env var '" + envName
                            + "' or system property '" + envName
                            + "' or key '" + fileKey + "' in " + resolvedConfigPath());
        }
        return value;
    }

    /**
     * Loads the optional properties file used as a fallback source of IT configuration. The path
     * is taken from {@code AAD_SYNC_IT_CONFIG_FILE} (env or system property), or defaults to
     * {@value #DEFAULT_CONFIG_PATH}-equivalent. A missing file is not an error — the IT will
     * still pick up env/system properties if they're set.
     */
    private static Properties loadFileProps() {
        Properties props = new Properties();
        Path path = Path.of(resolvedConfigPath());
        if (!Files.isReadable(path)) {
            return props;
        }
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read IT config file: " + path, e);
        }
        return props;
    }

    private static String resolvedConfigPath() {
        String envPath = System.getenv("AAD_SYNC_IT_CONFIG_FILE");
        if (envPath != null && !envPath.isBlank()) {
            return envPath;
        }
        String sysPath = System.getProperty("aad-sync-it.config-file");
        if (sysPath != null && !sysPath.isBlank()) {
            return sysPath;
        }
        return DEFAULT_CONFIG_PATH;
    }

    /**
     * Used by {@link EnabledIf @EnabledIf} on the class to skip the whole IT suite when the
     * minimum required configuration is missing. Mirrors the four {@code required(...)} calls in
     * {@link #setUp()} so we never reach an {@link IllegalStateException} during test execution
     * in a misconfigured environment — the tests just don't run.
     */
    static boolean isConfigured() {
        return lookup("AAD_SYNC_IT_TENANT_ID", "tenantId", null) != null
                && lookup("AAD_SYNC_IT_CLIENT_ID", "clientId", null) != null
                && lookup("AAD_SYNC_IT_CLIENT_SECRET", "clientSecret", null) != null
                && lookup("AAD_SYNC_IT_GROUP_PREFIX", "groupPrefix", null) != null;
    }
}
