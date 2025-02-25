package ch.sbb.polarion.extension.aad.synchronizer.utils;

import com.polarion.alm.shared.util.StringUtils;
import com.polarion.core.config.IOAuth2SecurityConfiguration;
import com.polarion.platform.internal.security.UserAccountVault;
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
    private final UserAccountVault userAccountVault;

    public OAuth2Client() {
        this.oAuthClient = new OAuthClient(new URLConnectionClient());
        this.userAccountVault = UserAccountVault.getInstance();
    }

    @VisibleForTesting
    public OAuth2Client(OAuthClient oAuthClient, UserAccountVault userAccountVault) {
        this.oAuthClient = oAuthClient;
        this.userAccountVault = userAccountVault;
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

    private @Nullable String getGraphApiClientSecretFromPolarionVault(@NotNull String graphApiClientSecretKey) {
        if (!StringUtils.isEmptyTrimmed(graphApiClientSecretKey)) {
            UserAccountVault.Credentials credentials = userAccountVault.getCredentialsForKey(graphApiClientSecretKey);
            return credentials.getPassword();
        }
        return null;
    }
}
