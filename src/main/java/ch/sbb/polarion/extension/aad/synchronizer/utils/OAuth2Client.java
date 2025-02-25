package ch.sbb.polarion.extension.aad.synchronizer.utils;

import com.polarion.core.config.IOAuth2SecurityConfiguration;
import org.apache.oltu.oauth2.client.OAuthClient;
import org.apache.oltu.oauth2.client.URLConnectionClient;
import org.apache.oltu.oauth2.client.request.OAuthClientRequest;
import org.apache.oltu.oauth2.client.response.OAuthJSONAccessTokenResponse;
import org.apache.oltu.oauth2.common.exception.OAuthProblemException;
import org.apache.oltu.oauth2.common.exception.OAuthSystemException;
import org.apache.oltu.oauth2.common.message.types.GrantType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;


public class OAuth2Client {

    private final OAuthClient oAuthClient;

    public OAuth2Client() {
        this.oAuthClient = new OAuthClient(new URLConnectionClient());
    }

    @VisibleForTesting
    public OAuth2Client(OAuthClient oAuthClient) {
        this.oAuthClient = oAuthClient;
    }

    public @NotNull String getToken(@Nullable String tokenUrl, @Nullable String clientId, @Nullable String clientSecret, @NotNull String scope) {
        try {
            OAuthClientRequest oAuthClientRequest = OAuthClientRequest
                    .tokenLocation(tokenUrl)
                    .setGrantType(GrantType.CLIENT_CREDENTIALS)
                    .setClientId(clientId)
                    .setClientSecret(clientSecret)
                    .setScope(scope)
                    .buildBodyMessage();

            OAuthJSONAccessTokenResponse oAuthJSONAccessTokenResponse = oAuthClient.accessToken(oAuthClientRequest);
            return oAuthJSONAccessTokenResponse.getAccessToken();
        } catch (OAuthSystemException | OAuthProblemException e) {
            throw new OAuth2Exception("Cannot obtain OAuth2 token: " + e.getMessage(), e);
        }
    }

    public @NotNull String getToken(@NotNull IOAuth2SecurityConfiguration auth2SecurityConfiguration) {
//        auth2SecurityConfiguration.clientSecretVaultKey();
        String tokenUrl = auth2SecurityConfiguration.tokenUrl();
        String clientId = auth2SecurityConfiguration.clientId();
        String clientSecret = auth2SecurityConfiguration.clientSecret();
        String scope = auth2SecurityConfiguration.scope();

        return getToken(tokenUrl, clientId, clientSecret, scope);
    }
}
