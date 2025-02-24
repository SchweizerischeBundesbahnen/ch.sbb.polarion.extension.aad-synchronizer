package ch.sbb.polarion.extension.aad.synchronizer.model;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static ch.sbb.polarion.extension.aad.synchronizer.model.OrganizationDataWrapper.ON_PREMISES_LAST_SYNC_DATE_TIME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OrganizationDataWrapperTest {

    @Test
    void testParseWithFieldsMapping() throws IOException {
        Map<String, String> fieldsMapping = Map.ofEntries(
                Map.entry(ON_PREMISES_LAST_SYNC_DATE_TIME, "onPremisesLastSyncDateTime")
        );

        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("organizationData.json")) {
            String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            OrganizationDataWrapper organizationDataWrapper = OrganizationDataWrapper.fromJsonList(content, fieldsMapping);
            assertNotNull(organizationDataWrapper);
            List<OrganizationData> organizationDataList = organizationDataWrapper.getValue();
            assertEquals(1, organizationDataList.size());
            assertEquals("Sun May 12 11:17:45 CEST 2024", organizationDataList.get(0).getOnPremisesLastSyncDateTime().toString());
        }
    }
}
