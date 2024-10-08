# Synchronization user job between Azure AD and Polarion

This Polarion job synchronizes deleted (or marked as inactive) Azure AD users with Polarion

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
<job id="aad_user_synchronization.job" cronExpression="0 0 0 * * ?" name="AAD Synchronization job" scope="system">
    <graphApiTokenUrl>https://login.microsoftonline.com/{tenant}/oauth2/v2.0/token</graphApiTokenUrl>
    <graphApiClientId>client_id_goes_here</graphApiClientId>
    <graphApiClientSecret>polarion_vault_key_for_client_secret</graphApiClientSecret>
    <graphApiScope>https://graph.microsoft.com/.default</graphApiScope>

    <groupPrefix>SOME_GROUP_PREFIX_</groupPrefix>
    <dryRun>true</dryRun>
    <checkLastSynchronization>false</checkLastSynchronization>
</job>
```
