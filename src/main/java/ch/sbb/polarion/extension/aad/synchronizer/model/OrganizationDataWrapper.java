package ch.sbb.polarion.extension.aad.synchronizer.model;

import ch.sbb.polarion.extension.aad.synchronizer.utils.JsonListParser;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrganizationDataWrapper {
    public static final String ON_PREMISES_LAST_SYNC_DATE_TIME = "onPremisesLastSyncDateTime";

    private List<OrganizationData> value;

    public static @NotNull OrganizationDataWrapper fromJsonList(@NotNull String json, @NotNull Map<String, String> fieldsMapping) {
        return JsonListParser.parseList(json, fieldsMapping, OrganizationDataWrapper.class, OrganizationData.class);
    }
}
