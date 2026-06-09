package ch.sbb.polarion.extension.aad.synchronizer.utils;

import ch.sbb.polarion.extension.aad.synchronizer.model.OrganizationData;
import ch.sbb.polarion.extension.aad.synchronizer.model.OrganizationDataWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static ch.sbb.polarion.extension.aad.synchronizer.model.OrganizationDataWrapper.ON_PREMISES_LAST_SYNC_DATE_TIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonListParserTest {

    private static final String FIELD = ON_PREMISES_LAST_SYNC_DATE_TIME;

    @Test
    void parseObjectResolvesNestedFieldPath() {
        String json = "{\"a\":{\"b\":\"2024-05-12T09:17:45Z\"}}";

        OrganizationData data = JsonListParser.parseObject(json, Map.of(FIELD, "a/b"), OrganizationData.class);

        assertThat(data.getOnPremisesLastSyncDateTime()).isEqualTo(Instant.parse("2024-05-12T09:17:45Z"));
    }

    @Test
    void parseObjectReturnsNullFieldWhenIntermediateSegmentIsMissing() {
        String json = "{\"x\":\"y\"}";

        OrganizationData data = JsonListParser.parseObject(json, Map.of(FIELD, "a/b"), OrganizationData.class);

        assertThat(data.getOnPremisesLastSyncDateTime()).isNull();
    }

    @Test
    void parseObjectReturnsNullFieldWhenJsonValueIsExplicitlyNull() {
        String json = "{\"a\":null}";

        OrganizationData data = JsonListParser.parseObject(json, Map.of(FIELD, "a"), OrganizationData.class);

        assertThat(data.getOnPremisesLastSyncDateTime()).isNull();
    }

    @Test
    void parseObjectHandlesNullMappedFieldName() {
        Map<String, String> mappingWithNullPath = new HashMap<>();
        mappingWithNullPath.put(FIELD, null);

        OrganizationData data = JsonListParser.parseObject("{\"a\":\"b\"}", mappingWithNullPath, OrganizationData.class);

        assertThat(data.getOnPremisesLastSyncDateTime()).isNull();
    }

    @Test
    void parseListReturnsEmptyWrapperWhenContainerIsAbsent() {
        OrganizationDataWrapper wrapper = JsonListParser.parseList(
                "{}", Map.of(FIELD, "onPremisesLastSyncDateTime"),
                OrganizationDataWrapper.class, OrganizationData.class);

        assertThat(wrapper.getValue()).isEmpty();
    }

    @Test
    void parseListRethrowsOnMalformedJson() {
        assertThatThrownBy(() -> JsonListParser.parseList(
                "not-json", Map.of(FIELD, "onPremisesLastSyncDateTime"),
                OrganizationDataWrapper.class, OrganizationData.class))
                .isInstanceOf(JsonProcessingException.class);
    }

    @Test
    void parseObjectRethrowsOnMalformedJson() {
        assertThatThrownBy(() -> JsonListParser.parseObject(
                "not-json", Map.of(FIELD, "onPremisesLastSyncDateTime"), OrganizationData.class))
                .isInstanceOf(JsonProcessingException.class);
    }
}
