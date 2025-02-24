package ch.sbb.polarion.extension.aad.synchronizer.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    public @NotNull <W, E> W parseList(@NotNull String json, @NotNull Map<String, String> fieldsMapping, @NotNull Class<W> wrapperClass, @NotNull Class<E> elementClass) {
        return parseList(json, VALUE, fieldsMapping, wrapperClass, elementClass);
    }

    @SneakyThrows
    public @NotNull <W, E> W parseList(@NotNull String json, @NotNull String containerName, @NotNull Map<String, String> fieldsMapping, Class<W> wrapperClass, Class<E> elementClass) {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode = objectMapper.readTree(json);
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
        return (fieldName != null && node.has(fieldName)) ? node.get(fieldName).asText() : null;
    }
}
