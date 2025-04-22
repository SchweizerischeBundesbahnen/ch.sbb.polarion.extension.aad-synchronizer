package ch.sbb.polarion.extension.aad.synchronizer.utils;

import ch.sbb.polarion.extension.aad.synchronizer.model.PageableWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polarion.core.util.StringUtils;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@UtilityClass
public class JsonListParser {
    public static final String VALUE = "value";
    public static final String NEXT_LINK = "@odata.nextLink";

    @SneakyThrows
    public @NotNull <W, E> W parseList(@NotNull String json, @NotNull Map<String, String> fieldsMapping, @NotNull Class<W> wrapperClass, @NotNull Class<E> elementClass) {
        JsonNode rootNode = getObjectMapper().readTree(json);
        W wrapper = parseList(rootNode, VALUE, fieldsMapping, wrapperClass, elementClass);
        if (wrapper instanceof PageableWrapper pageableWrapper) {
            String nextLink = getFieldValue(rootNode, NEXT_LINK);
            if (!StringUtils.isEmpty(nextLink)) {
                pageableWrapper.setNextLink(nextLink);
            }
        }
        return wrapper;
    }

    public @NotNull <W, E> W parseList(@NotNull JsonNode rootNode, @NotNull String containerName, @NotNull Map<String, String> fieldsMapping, Class<W> wrapperClass, Class<E> elementClass) {
        ObjectMapper objectMapper = getObjectMapper();
        List<E> items = new ArrayList<>();

        if (rootNode.has(containerName)) {
            for (JsonNode itemNode : rootNode.get(containerName)) {
                Map<String, String> fieldData = parseFields(itemNode, fieldsMapping);
                E item = objectMapper.convertValue(fieldData, elementClass);
                items.add(item);
            }
        }

        return objectMapper.convertValue(Map.of(containerName, items), wrapperClass);
    }

    private @NotNull Map<String, String> parseFields(@NotNull JsonNode node, @NotNull Map<String, String> fieldsMapping) {
        Map<String, String> mappedValues = new HashMap<>();
        for (Map.Entry<String, String> entry : fieldsMapping.entrySet()) {
            mappedValues.put(entry.getKey(), getFieldValue(node, entry.getValue()));
        }
        return mappedValues;
    }

    private @Nullable String getFieldValue(@NotNull JsonNode node, @Nullable String fieldName) {
        if (fieldName != null && node.has(fieldName)) {
            JsonNode jsonNode = node.get(fieldName);
            if (jsonNode.isNull()) {
                return null;
            }
            return jsonNode.asText();
        } else {
            return null;
        }
    }

    private ObjectMapper getObjectMapper() {
        return new ObjectMapper();
    }
}
