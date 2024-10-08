package ch.sbb.polarion.extension.aad.synchronizer.utils;

import org.apache.oltu.oauth2.client.OAuthClient;
import org.apache.oltu.oauth2.client.URLConnectionClient;
import org.apache.oltu.oauth2.client.request.OAuthClientRequest;
import org.apache.oltu.oauth2.client.response.OAuthJSONAccessTokenResponse;
import org.apache.oltu.oauth2.common.exception.OAuthProblemException;
import org.apache.oltu.oauth2.common.exception.OAuthSystemException;
import org.apache.oltu.oauth2.common.message.types.GrantType;
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

    public String getToken(String tokenUrl, String clientId, String clientSecret, String scope) {
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
            throw new OAuth2Exception("Cannot obtain OAuth token: " + e.getMessage(), e);
        }
    }
}
