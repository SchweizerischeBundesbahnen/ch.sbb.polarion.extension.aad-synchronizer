package ch.sbb.polarion.extension.aad.synchronizer.utils;

import org.apache.oltu.oauth2.client.OAuthClient;
import org.apache.oltu.oauth2.client.request.OAuthClientRequest;
import org.apache.oltu.oauth2.client.response.OAuthJSONAccessTokenResponse;
import org.apache.oltu.oauth2.common.exception.OAuthProblemException;
import org.apache.oltu.oauth2.common.exception.OAuthSystemException;
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

    @InjectMocks
    private OAuth2Client oAuth2Client;

    @Test
    void shouldSuccessfullyCallOAuthClient() throws OAuthProblemException, OAuthSystemException {
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
}