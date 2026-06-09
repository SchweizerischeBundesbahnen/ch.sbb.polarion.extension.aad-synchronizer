package ch.sbb.polarion.extension.aad.synchronizer.utils;

import ch.sbb.polarion.extension.aad.synchronizer.connector.FakeOAuth2SecurityConfiguration;
import com.polarion.platform.internal.security.UserAccountVault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuth2ClientTest {
    private static final String TOKEN_URL = "https://login.example.com/oauth2/v2.0/token";

    @Mock
    private HttpClient httpClient;
    @Mock
    private UserAccountVault userAccountVault;

    @InjectMocks
    private OAuth2Client oAuth2Client;

    @Test
    void shouldReturnAccessTokenFromTokenEndpoint() throws IOException, InterruptedException {
        stubTokenResponse(200, "{\"token_type\":\"Bearer\",\"access_token\":\"testToken\"}");

        String token = oAuth2Client.getToken(TOKEN_URL, "TestClientId", "testClientSecret", "testScope");

        assertThat(token).isEqualTo("testToken");
        HttpRequest request = captureSentRequest();
        assertThat(request.uri()).hasToString(TOKEN_URL);
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.headers().firstValue("Content-Type")).contains("application/x-www-form-urlencoded");
    }

    @Test
    void shouldBuildFormEncodedBodyWithAllParameters() {
        String body = OAuth2Client.buildFormBody("TestClientId", "testClientSecret", "https://graph.microsoft.com/.default");

        assertThat(body).isEqualTo("grant_type=client_credentials&client_id=TestClientId&client_secret=testClientSecret&scope=https%3A%2F%2Fgraph.microsoft.com%2F.default");
    }

    @Test
    void shouldOmitOptionalParametersWhenNull() {
        String body = OAuth2Client.buildFormBody(null, null, null);

        assertThat(body).isEqualTo("grant_type=client_credentials");
    }

    @Test
    void shouldPropagateTransportError() throws IOException, InterruptedException {
        doThrow(new IOException("connection refused")).when(httpClient).send(any(HttpRequest.class), any());

        assertThatThrownBy(() -> oAuth2Client.getToken(TOKEN_URL, "TestClientId", "testClientSecret", "testScope"))
                .isInstanceOf(OAuth2Exception.class)
                .hasMessageContaining("connection refused");
    }

    @Test
    void shouldPropagateInterruption() throws IOException, InterruptedException {
        doThrow(new InterruptedException("interrupted")).when(httpClient).send(any(HttpRequest.class), any());

        try {
            assertThatThrownBy(() -> oAuth2Client.getToken(TOKEN_URL, "TestClientId", "testClientSecret", "testScope"))
                    .isInstanceOf(OAuth2Exception.class)
                    .hasMessageContaining("interrupted");
            assertThat(Thread.currentThread().isInterrupted())
                    .as("interrupt status must be restored after catching InterruptedException")
                    .isTrue();
        } finally {
            // Clear the interrupt flag we just asserted on so it doesn't leak into other tests.
            Thread.interrupted();
        }
    }

    @Test
    void shouldFailWhenTokenEndpointReturnsErrorStatus() throws IOException, InterruptedException {
        stubTokenResponse(401, "{\"error\":\"invalid_client\"}");

        assertThatThrownBy(() -> oAuth2Client.getToken(TOKEN_URL, "TestClientId", "testClientSecret", "testScope"))
                .isInstanceOf(OAuth2Exception.class)
                .hasMessageContaining("HTTP 401")
                .hasMessageContaining("invalid_client");
    }

    @Test
    void shouldFailWhenTokenEndpointReturnsInformationalStatus() throws IOException, InterruptedException {
        stubTokenResponse(199, "unexpected");

        assertThatThrownBy(() -> oAuth2Client.getToken(TOKEN_URL, "TestClientId", "testClientSecret", "testScope"))
                .isInstanceOf(OAuth2Exception.class)
                .hasMessageContaining("HTTP 199");
    }

    @Test
    void shouldWrapNullTokenUrlInOAuth2Exception() {
        assertThatThrownBy(() -> oAuth2Client.getToken(null, "TestClientId", "testClientSecret", "testScope"))
                .isInstanceOf(OAuth2Exception.class)
                .hasMessageContaining("token URL is not configured");
    }

    @Test
    void shouldWrapMalformedTokenUrlInOAuth2Exception() {
        assertThatThrownBy(() -> oAuth2Client.getToken("not a valid url", "TestClientId", "testClientSecret", "testScope"))
                .isInstanceOf(OAuth2Exception.class)
                .hasMessageContaining("Cannot obtain OAuth2 token");
    }

    @Test
    void shouldFailWhenResponseHasNoAccessToken() throws IOException, InterruptedException {
        stubTokenResponse(200, "{\"token_type\":\"Bearer\"}");

        assertThatThrownBy(() -> oAuth2Client.getToken(TOKEN_URL, "TestClientId", "testClientSecret", "testScope"))
                .isInstanceOf(OAuth2Exception.class);
    }

    @Test
    void shouldSuccessfullyCallTokenEndpointWithAuth2SecurityConfiguration() throws IOException, InterruptedException {
        stubTokenResponse(200, "{\"access_token\":\"testToken\"}");

        FakeOAuth2SecurityConfiguration fakeOAuth2SecurityConfiguration = new FakeOAuth2SecurityConfiguration();
        String token = oAuth2Client.getToken(fakeOAuth2SecurityConfiguration);

        assertThat(token).isEqualTo("testToken");
        HttpRequest request = captureSentRequest();
        assertThat(request.uri()).hasToString(fakeOAuth2SecurityConfiguration.tokenUrl());
    }

    @Test
    void shouldResolveClientSecretFromVault() throws IOException, InterruptedException {
        stubTokenResponse(200, "{\"access_token\":\"testToken\"}");
        when(userAccountVault.getCredentialsForKey("clientSecretVaultKey"))
                .thenReturn(new UserAccountVault.Credentials("clientId", "clientSecret"));

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
        verify(userAccountVault).getCredentialsForKey("clientSecretVaultKey");
    }

    @Test
    void defaultConstructorInitialisesCollaborators() {
        try (MockedStatic<UserAccountVault> vault = mockStatic(UserAccountVault.class)) {
            vault.when(UserAccountVault::getInstance).thenReturn(userAccountVault);

            assertThat(new OAuth2Client()).isNotNull();
        }
    }

    @Test
    void shouldNotQueryVaultWhenVaultKeyIsBlank() throws IOException, InterruptedException {
        // A non-null but blank clientSecretVaultKey must short-circuit to a null secret without
        // touching the vault (covers the false branch of the isEmptyTrimmed guard).
        stubTokenResponse(200, "{\"access_token\":\"testToken\"}");

        FakeOAuth2SecurityConfiguration configWithBlankVaultKey = new FakeOAuth2SecurityConfiguration() {
            @Override
            public @Nullable String clientSecret() {
                return null;
            }

            @Override
            public @Nullable String clientSecretVaultKey() {
                return "   ";
            }
        };

        assertThat(oAuth2Client.getToken(configWithBlankVaultKey)).isEqualTo("testToken");
        verifyNoInteractions(userAccountVault);
    }

    @SuppressWarnings("unchecked")
    private void stubTokenResponse(int statusCode, String body) throws IOException, InterruptedException {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.body()).thenReturn(body);
        doReturn(response).when(httpClient).send(any(HttpRequest.class), any());
    }

    private HttpRequest captureSentRequest() throws IOException, InterruptedException {
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any());
        return requestCaptor.getValue();
    }
}
