package ch.sbb.polarion.extension.aad.synchronizer.connector;

import com.polarion.core.config.IAuthProfileMappingConfiguration;
import com.polarion.core.config.ICreateAccountConfiguration;
import com.polarion.core.config.ILoginProviderViewConfiguration;
import com.polarion.core.config.IOAuth2SecurityConfiguration;
import com.polarion.core.config.ISsoGroupsConfiguration;
import com.polarion.core.config.auth.xml.Entitlements;
import com.polarion.core.config.auth.xml.TokenExchangeParameters;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class FakeOAuth2SecurityConfiguration implements IOAuth2SecurityConfiguration {
    @Override
    public @NotNull String responseParser() {
        return "";
    }

    @Override
    public @Nullable String authorizeUrl() {
        return "";
    }

    @Override
    public @Nullable String tokenUrl() {
        return "https://login.microsoftonline.com/{tenant}/oauth2/v2.0/token";
    }

    @Override
    public @NotNull String tokenUrlMethod() {
        return "POST";
    }

    @Override
    public @Nullable String userUrl() {
        return "";
    }

    @Override
    public @Nullable String accessKeyInfoUrl() {
        return "";
    }

    @Override
    public @Nullable String introspectionUrl() {
        return "";
    }

    @Override
    public @NotNull List<AuthUsedFor> usedFor() {
        return List.of();
    }

    @Override
    public boolean nonce() {
        return false;
    }

    @Override
    public @Nullable String clientId() {
        return "clientId";
    }

    @Override
    public @Nullable String clientSecret() {
        return "clientSecret";
    }

    @Override
    public @Nullable String clientSecretVaultKey() {
        return "";
    }

    @Override
    public @NotNull String scope() {
        return "https://graph.microsoft.com/.default";
    }

    @Override
    public @Nullable String paramFetchUrl(@NotNull String s) {
        return "";
    }

    @Override
    public @NotNull IAuthProfileMappingConfiguration mapping() {
        return new IAuthProfileMappingConfiguration() {
            @Override
            public @NotNull String id() {
                return "mailNickname";
            }

            @Override
            public @NotNull String name() {
                return "displayName";
            }

            @Override
            public @NotNull String email() {
                return "mail";
            }

            @Override
            public @Nullable String description() {
                return null;
            }
        };
    }

    @Override
    public @Nullable Entitlements entitlements() {
        return null;
    }

    @Override
    public @Nullable TokenExchangeParameters tokenExchangeParameters() {
        return null;
    }

    @Override
    public int profileRequestConnectionTimeout() {
        return 0;
    }

    @Override
    public boolean isTraceSsoResponsesEnabled() {
        return false;
    }

    @Override
    public boolean forceLdapEsignatures() {
        return false;
    }

    @Override
    public boolean replaceForbiddenCharactersEnabled() {
        return false;
    }

    @Override
    public @NotNull ISsoGroupsConfiguration groupsSynchronization() {
        return null;
    }

    @Override
    public @NotNull String id() {
        return "authenticationProviderId";
    }

    @Override
    public @NotNull ICreateAccountConfiguration autocreate() {
        return null;
    }

    @Override
    public boolean isDefault() {
        return false;
    }

    @Override
    public @NotNull ILoginProviderViewConfiguration view() {
        return null;
    }
}
