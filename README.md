# Synchronization user job between Azure AD and Polarion

This Polarion job synchronizes deleted (or marked as inactive) Azure AD users with Polarion

> [!IMPORTANT]
> Starting from version 3.0.0 only latest version of Polarion is supported.
> Right now it is Polarion 2410.

## Quick start

The latest version of the extension can be downloaded from the [releases page](../../releases/latest) and installed to Polarion instance without necessity to be compiled from the sources.
The extension should be copied to `<polarion_home>/polarion/extensions/ch.sbb.polarion.extension.aad-synchronizer/eclipse/plugins` and changes will take effect after Polarion restart.
> [!IMPORTANT]
> Don't forget to clear `<polarion_home>/data/workspace/.config` folder after extension installation/update to make it work properly.

## Build

This extension can be produced using maven:

```bash
mvn clean package
```

## Installation to Polarion

To install the extension to Polarion `ch.sbb.polarion.extension.aad-synchronizer-<version>.jar`
should be copied to `<polarion_home>/polarion/extensions/ch.sbb.polarion.extension.aad-synchronizer/eclipse/plugins`
It can be done manually or automated using maven build:

```bash
mvn clean install -P install-to-local-polarion
```

For automated installation with maven env variable `POLARION_HOME` should be defined and point to folder where Polarion is installed.

Changes only take effect after restart of Polarion.

## Polarion configuration

For using this job you can go in the global `Administration` / `Scheduler` to run regularly like this:

```xml
<job id="aad_user_synchronization.job" cronExpression="0 0 0 * * ?" name="AAD Synchronization" scope="system">
    <graphApiTokenUrl>https://login.microsoftonline.com/{tenant}/oauth2/v2.0/token</graphApiTokenUrl>
    <graphApiClientId>client_id_goes_here</graphApiClientId>
    <graphApiClientSecret>polarion_vault_key_for_client_secret</graphApiClientSecret>
    <graphApiScope>https://graph.microsoft.com/.default</graphApiScope>

    <groupPrefix>SOME_GROUP_PREFIX_</groupPrefix>

    <whitelist>
        <filter>^user\d{3}@example\.com$</filter>
        <accounts>
            <account>userAAA@example.com</account>
            <account>userBBB@example.com</account>
        </accounts>
    </whitelist>
    <blacklist>
        <filter>^admin|guest|service$</filter>
        <accounts>
            <account>testadmin@example.com</account>
            <account>root@example.com</account>
        </accounts>
    </blacklist>

    <dryRun>true</dryRun>
    <checkLastSynchronization>false</checkLastSynchronization>
</job>
```

### Parameters overview

This configuration defines the parameters for a job that synchronizes users from Azure Active Directory (AAD) to the Polarion.

#### Azure Graph API Configuration

- **Graph API Token URL** (`graphApiTokenUrl`): URL to obtain authentication tokens for Azure Graph API.
- **Graph API Client ID** (`graphApiClientId`): Identifier for the Azure AD application used for API access.
- **Graph API Client Secret** (`graphApiClientSecret`): Client secret key for authenticating API requests stored in Polarion User Vault.
- **Graph API Scope** (`graphApiScope`): Scope for the Graph API.

#### Group Synchronization

- **Group Prefix** (`groupPrefix`): Limits synchronization to groups with a specified naming prefix.

#### User Filters

##### Whitelist (`whitelist`)

- **Filter** (`filter`): A regex pattern to include matching users.
- **Accounts** (`accounts`): A predefined list of users to include in synchronization.

If both `filter` and `accounts` are not specified, all found users will be synchronized.
If both `filter` and `accounts` are provided, the users that match the filter or are explicitly listed will be synchronized.

##### Blacklist (`blacklist`)

- **Filter** (`filter`): A regex pattern to exclude matching users.
- **Accounts** (`accounts`): A predefined list of users to always exclude from synchronization.

If both `filter` and `accounts` are not specified, no users will be excluded.
If both `filter` and `accounts` are provided, the users that match the filter or are explicitly listed will be excluded from synchronization.

#### Additional Settings

- **Dry Run** (`dryRun`): Enables simulation mode, where no actual changes are made.
- **Check Last Synchronization** (`checkLastSynchronization`): Determines whether to verify the timestamp of the last synchronization before execution.

### REST API

This extension provides REST API. OpenAPI Specification can be obtained [here](docs/openapi.json).
