[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.pdf-exporter&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.aad-synchronizer)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.aad-synchronizer&metric=bugs)](https://sonarcloud.io/summary/new_code?id=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.aad-synchronizer)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.aad-synchronizer&metric=code_smells)](https://sonarcloud.io/summary/new_code?id=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.aad-synchronizer)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.aad-synchronizer&metric=coverage)](https://sonarcloud.io/summary/new_code?id=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.aad-synchronizer)
[![Duplicated Lines (%)](https://sonarcloud.io/api/project_badges/measure?project=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.aad-synchronizer&metric=duplicated_lines_density)](https://sonarcloud.io/summary/new_code?id=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.aad-synchronizer)
[![Lines of Code](https://sonarcloud.io/api/project_badges/measure?project=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.aad-synchronizer&metric=ncloc)](https://sonarcloud.io/summary/new_code?id=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.aad-synchronizer)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.aad-synchronizer&metric=reliability_rating)](https://sonarcloud.io/summary/new_code?id=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.aad-synchronizer)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.aad-synchronizer&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.aad-synchronizer)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.aad-synchronizer&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.aad-synchronizer)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.aad-synchronizer&metric=vulnerabilities)](https://sonarcloud.io/summary/new_code?id=SchweizerischeBundesbahnen_ch.sbb.polarion.extension.aad-synchronizer)

# User synchronization job between Azure AD and Polarion

This Polarion job synchronizes users between Azure AD groups and Polarion: it creates Polarion accounts
for users that were added to the configured AAD groups and removes (or marks as inactive) Polarion
accounts for users that no longer belong to any of those groups.

> [!IMPORTANT]
> Starting from version 3.0.0 only latest version of Polarion is supported.
> Right now it is Polarion 2512.

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

To run this job on a schedule, configure it in the global `Administration` / `Scheduler` as follows:

```xml
<job id="aad_user_synchronization.job" cronExpression="0 0 0 * * ?" name="AAD Synchronization" scope="system">
    <authenticationProviderId>oauth2</authenticationProviderId>

    <!-- Optional: enable directory schema extension expansion (see "Custom extension attributes" below) -->
    <extensionAppId>abc123de-f456-7890-abcd-ef1234567890</extensionAppId>
    <extensionFields>id</extensionFields>

    <!-- Optional: override Graph property names when they differ from authentication.xml <mapping> -->
    <graphIdField>onPremisesSamAccountName</graphIdField>

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

This configuration defines the parameters for the job that synchronizes users from Azure Active Directory (AAD) to Polarion.

#### Authentication Provider Configuration

- **Authentication Provider** (`authenticationProviderId`): the OAuth2 provider from `authentication.xml` used to obtain authentication tokens for the Microsoft Graph API and to determine the field mappings.

The extension uses `tokenUrl`, `clientId`, `clientSecret`, `scope` and `mapping` from this provider to talk to the MS Graph API.

Example of `authentication.xml`:

```xml
<?xml version="1.0" encoding="UTF-8" standalone="no" ?>
<authentication xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xmlns="http://polarion.com/PolarionAuthentication"
                xsi:schemaLocation="http://polarion.com/PolarionAuthentication http://localhost/polarion/authentication.xsd">

    <password default="true"/>

    <oauth2 id="oauth2">
        <nonce/>
        <view>
            <text>Single sign-on</text>
        </view>

        <authorizeUrl>https://login.microsoftonline.com/{tenant}/oauth2/v2.0/authorize</authorizeUrl>

        <tokenUrl>https://login.microsoftonline.com/{tenant}/oauth2/v2.0/token</tokenUrl>
        <clientId>client_id_goes_here</clientId>
        <clientSecret userAccountVaultKey="polarion_vault_key_for_client_secret"/>
        <scopes>
            <scope>https://graph.microsoft.com/.default</scope>
        </scopes>

        <mapping>
            <id>mailNickname</id>
            <name>displayName</name>
            <email>mail</email>
        </mapping>

        <autocreate>
            <enabled>true</enabled>
            <globalRoles>
                <role>user</role>
            </globalRoles>
        </autocreate>

        <groupsSynchronization>
            <enabled>true</enabled>
            <groupsMapping>
                <namePath>roles</namePath>
            </groupsMapping>
        </groupsSynchronization>
    </oauth2>
</authentication>
```

#### Custom extension attributes

The fields configured in the `<mapping>` block of `authentication.xml` are used by the synchronization
job as Microsoft Graph property names when fetching user data from `/users/{id}`. For standard properties
like `displayName`, `mail`, `mailNickname` this works out of the box.

To use an [Azure AD directory schema extension](https://learn.microsoft.com/en-us/graph/extensibility-overview)
as one of those fields, Microsoft Graph requires the property to be referenced by its fully-qualified name
`extension_<appIdWithoutDashes>_<name>`. There are two equivalent ways to configure this:

1. **Put the full name into `authentication.xml`** — works without any extra job parameters, but requires
   the same name on the JWT side too (which usually means setting up an Azure claims mapping policy).
2. **Keep the bare name in `authentication.xml` and let the job expand it.** Set the following two job
   parameters and the synchronizer will rewrite the listed mapping fields to
   `extension_<appIdWithoutDashes>_<name>` before talking to Graph:

   - **`extensionAppId`**: AAD application (client) ID owning the directory schema extension(s). Dashes
     are stripped automatically when building the Graph property name.
   - **`extensionFields`**: comma-separated list of mapping fields to expand — any combination of `id`,
     `name`, `email`. When omitted but `extensionAppId` is set, defaults to `id`.

Values that already start with `extension_` or that contain `/` (nested property paths such as
`onPremisesExtensionAttributes/extensionAttribute1`) are passed through unchanged, so you can mix
expanded and verbatim entries freely.

**Example.** With this job configuration:

```xml
<extensionAppId>abc123de-f456-7890-abcd-ef1234567890</extensionAppId>
<extensionFields>id,email</extensionFields>
```

and this `authentication.xml` mapping:

```xml
<mapping>
    <id>mycustomid</id>          <!-- bare → extension_abc123def4567890abcdef1234567890_mycustomid -->
    <name>displayName</name>     <!-- standard property, untouched -->
    <email>myworkmail</email>    <!-- bare → extension_abc123def4567890abcdef1234567890_myworkmail -->
</mapping>
```

the synchronizer will query Graph with `$select=extension_abc123def4567890abcdef1234567890_mycustomid,displayName,extension_abc123def4567890abcdef1234567890_myworkmail`
when fetching each user, and use the resolved `extension_..._mycustomid` value as the Polarion user identifier.

> [!NOTE]
> Members of the AAD group are first listed via `/groups/{id}/members` to obtain their AAD object IDs,
> and then each user is fetched individually via `/users/{aadObjectId}`. The per-user call is required
> because the `/groups/{id}/members` endpoint returns directory objects that strip extension attributes
> via `$select`.

#### Overriding Graph property names per mapping field

The `<mapping>` block in `authentication.xml` is shared between Polarion (which uses it at login
time to read claims from the OAuth2 token) and this synchronizer (which uses it at sync time as the
Microsoft Graph property names). When the claim name on the token side differs from the property
name on the Graph side, point the synchronizer at the correct Graph property via one of these
optional job parameters:

- **`graphIdField`**: Graph user property to use as the Polarion identifier.
- **`graphNameField`**: Graph user property to use as the display name.
- **`graphEmailField`**: Graph user property to use as the email.

Each override, when set to a non-blank value, replaces the corresponding `<mapping>` entry in the
Graph `$select` verbatim — no `extension_...` auto-expansion is applied to the overridden value, so
you can put either a standard built-in property (`onPremisesSamAccountName`, `userPrincipalName`, …)
or the fully-qualified name of a directory schema extension (`extension_<appIdNoDashes>_<field>`).

**Example — standard Graph property under a different name.** The OAuth2 token exposes a custom
`sbbuid` claim; in Graph the same logical identifier is stored in the built-in
`onPremisesSamAccountName` property:

```xml
<!-- authentication.xml: claim names Polarion reads from the token -->
<mapping>
    <id>sbbuid</id>
    <name>displayName</name>
    <email>mail</email>
</mapping>
```

```xml
<!-- job configuration: Graph property names the synchronizer queries -->
<graphIdField>onPremisesSamAccountName</graphIdField>
```

The synchronizer will query Graph with `$select=onPremisesSamAccountName,displayName,mail` and use
the `onPremisesSamAccountName` value as the Polarion user identifier. Polarion keeps using the
`sbbuid` claim at login time because `<mapping>` is unchanged. No directory schema extension is
needed because `onPremisesSamAccountName` is a standard built-in Graph property.

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
