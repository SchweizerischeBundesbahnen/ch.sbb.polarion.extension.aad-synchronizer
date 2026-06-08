package ch.sbb.polarion.extension.aad.synchronizer.utils;

import com.polarion.alm.shared.util.StringUtils;
import com.polarion.core.config.IOAuth2SecurityConfiguration;
import com.polarion.platform.internal.security.UserAccountVault;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Minimal OAuth2 client-credentials helper used to obtain a Microsoft Entra ID access token.
 *
 * <p>Implemented directly on top of the JDK {@link HttpClient} and {@link JSONObject}. The previous
 * implementation relied on Apache Oltu, which is abandoned and still references {@code javax.servlet};
 * its bundled jars were rejected by Polarion 2606's Jakarta-compatibility scanner at startup.</p>
 */
public class OAuth2Client {

    private static final String GRANT_TYPE_CLIENT_CREDENTIALS = "client_credentials";
    private static final String ACCESS_TOKEN_FIELD = "access_token";

    private final HttpClient httpClient;
    private final UserAccountVault userAccountVault;

    public OAuth2Client() {
        this.httpClient = HttpClient.newHttpClient();
        this.userAccountVault = UserAccountVault.getInstance();
    }

    @VisibleForTesting
    public OAuth2Client(HttpClient httpClient, UserAccountVault userAccountVault) {
        this.httpClient = httpClient;
        this.userAccountVault = userAccountVault;
    }

    public @NotNull String getToken(@Nullable String tokenUrl, @Nullable String clientId, @Nullable String clientSecret, @NotNull String scope) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(buildFormBody(clientId, clientSecret, scope), StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw new OAuth2Exception("Cannot obtain OAuth2 token: token endpoint responded with HTTP " + status + " - " + response.body(), null);
            }
            return new JSONObject(response.body()).getString(ACCESS_TOKEN_FIELD);
        } catch (IOException | JSONException e) {
            throw new OAuth2Exception("Cannot obtain OAuth2 token: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OAuth2Exception("Cannot obtain OAuth2 token: " + e.getMessage(), e);
        }
    }

    public @NotNull String getToken(@NotNull IOAuth2SecurityConfiguration auth2SecurityConfiguration) {
        String tokenUrl = auth2SecurityConfiguration.tokenUrl();
        String clientId = auth2SecurityConfiguration.clientId();

        String clientSecret;
        String clientSecretVaultKey = auth2SecurityConfiguration.clientSecretVaultKey();
        if (clientSecretVaultKey != null) {
            clientSecret = getGraphApiClientSecretFromPolarionVault(clientSecretVaultKey);
        } else {
            clientSecret = auth2SecurityConfiguration.clientSecret();
        }

        String scope = auth2SecurityConfiguration.scope();

        return getToken(tokenUrl, clientId, clientSecret, scope);
    }

    /**
     * Builds the {@code application/x-www-form-urlencoded} request body for the client-credentials
     * grant. {@code grant_type} is always present; {@code client_id}, {@code client_secret} and
     * {@code scope} are included only when non-null, mirroring the behaviour of the previous Oltu
     * request builder.
     */
    @VisibleForTesting
    static @NotNull String buildFormBody(@Nullable String clientId, @Nullable String clientSecret, @Nullable String scope) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("grant_type", GRANT_TYPE_CLIENT_CREDENTIALS);
        if (clientId != null) {
            params.put("client_id", clientId);
        }
        if (clientSecret != null) {
            params.put("client_secret", clientSecret);
        }
        if (scope != null) {
            params.put("scope", scope);
        }
        return params.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private static @NotNull String encode(@NotNull String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private @Nullable String getGraphApiClientSecretFromPolarionVault(@NotNull String graphApiClientSecretKey) {
        if (!StringUtils.isEmptyTrimmed(graphApiClientSecretKey)) {
            UserAccountVault.Credentials credentials = userAccountVault.getCredentialsForKey(graphApiClientSecretKey);
            return credentials.getPassword();
        }
        return null;
    }
}
