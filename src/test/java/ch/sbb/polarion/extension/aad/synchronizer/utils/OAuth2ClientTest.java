package ch.sbb.polarion.extension.aad.synchronizer.utils;

import ch.sbb.polarion.extension.aad.synchronizer.connector.FakeOAuth2SecurityConfiguration;
import com.polarion.platform.internal.security.UserAccountVault;
import org.apache.oltu.oauth2.client.OAuthClient;
import org.apache.oltu.oauth2.client.request.OAuthClientRequest;
import org.apache.oltu.oauth2.client.response.OAuthJSONAccessTokenResponse;
import org.apache.oltu.oauth2.common.exception.OAuthProblemException;
import org.apache.oltu.oauth2.common.exception.OAuthSystemException;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuth2ClientTest {
    @Mock
    private OAuthClient oAuthClient;
    @Mock
    private UserAccountVault userAccountVault;

    @InjectMocks
    private OAuth2Client oAuth2Client;

    @Test
    void shouldSuccessfullyCallOAuthClientWithParameters() throws OAuthProblemException, OAuthSystemException {
        OAuthJSONAccessTokenResponse response = mock(OAuthJSONAccessTokenResponse.class);
        when(response.getAccessToken()).thenReturn("testToken");
        when(oAuthClient.accessToken(any(OAuthClientRequest.class))).thenReturn(response);

        String token = oAuth2Client.getToken("testTokenUrl", "TestClientId", "testClientSecret", "testScope");

        assertThat(token).isEqualTo("testToken");
        ArgumentCaptor<OAuthClientRequest> requestCaptor = ArgumentCaptor.forClass(OAuthClientRequest.class);
        verify(oAuthClient).accessToken(requestCaptor.capture());
        OAuthClientRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.getBody()).isEqualTo("grant_type=client_credentials&scope=testScope&client_secret=testClientSecret&client_id=TestClientId");
    }

    @Test
    void shouldPropagateOAuthClientError() throws OAuthProblemException, OAuthSystemException {
        when(oAuthClient.accessToken(any(OAuthClientRequest.class))).thenThrow(new OAuthSystemException("Test error"));

        assertThatThrownBy(() -> oAuth2Client.getToken("testTokenUrl", "TestClientId", "testClientSecret", "testScope"))
                .isInstanceOf(OAuth2Exception.class)
                .hasMessageContaining("Test error");
    }

    @Test
    void shouldSuccessfullyCallOAuthClientWithAuth2SecurityConfiguration() throws OAuthProblemException, OAuthSystemException {
        OAuthJSONAccessTokenResponse response = mock(OAuthJSONAccessTokenResponse.class);
        when(response.getAccessToken()).thenReturn("testToken");
        when(oAuthClient.accessToken(any(OAuthClientRequest.class))).thenReturn(response);

        FakeOAuth2SecurityConfiguration fakeOAuth2SecurityConfiguration = new FakeOAuth2SecurityConfiguration();
        String token = oAuth2Client.getToken(fakeOAuth2SecurityConfiguration);

        assertThat(token).isEqualTo("testToken");
        ArgumentCaptor<OAuthClientRequest> requestCaptor = ArgumentCaptor.forClass(OAuthClientRequest.class);
        verify(oAuthClient).accessToken(requestCaptor.capture());
        OAuthClientRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.getBody()).isEqualTo("grant_type=client_credentials&scope=https%3A%2F%2Fgraph.microsoft.com%2F.default&client_secret=clientSecret&client_id=clientId");
    }

    @Test
    void shouldSuccessfullyCallOAuthClientWithAuth2SecurityConfiguration_ClientIdInVault() throws OAuthProblemException, OAuthSystemException {
        OAuthJSONAccessTokenResponse response = mock(OAuthJSONAccessTokenResponse.class);
        when(response.getAccessToken()).thenReturn("testToken");
        when(oAuthClient.accessToken(any(OAuthClientRequest.class))).thenReturn(response);
        when(userAccountVault.getCredentialsForKey("clientSecretVaultKey")).thenReturn(new UserAccountVault.Credentials("clientId", "clientSecret"));

        FakeOAuth2SecurityConfiguration fakeOAuth2SecurityConfiguration = new FakeOAuth2SecurityConfiguration() {
            @Override
            public @Nullable String clientSecret() {
                return null;
            }

            @Override
            public @Nullable String clientSecretVaultKey() {
                return "clientSecretVaultKey";
            }
        };
        String token = oAuth2Client.getToken(fakeOAuth2SecurityConfiguration);

        assertThat(token).isEqualTo("testToken");
        ArgumentCaptor<OAuthClientRequest> requestCaptor = ArgumentCaptor.forClass(OAuthClientRequest.class);
        verify(oAuthClient).accessToken(requestCaptor.capture());
        OAuthClientRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.getBody()).isEqualTo("grant_type=client_credentials&scope=https%3A%2F%2Fgraph.microsoft.com%2F.default&client_secret=clientSecret&client_id=clientId");
    }

}
