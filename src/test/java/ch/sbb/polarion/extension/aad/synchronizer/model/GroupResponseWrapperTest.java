package ch.sbb.polarion.extension.aad.synchronizer.model;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static ch.sbb.polarion.extension.aad.synchronizer.model.GroupResponseWrapper.ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GroupResponseWrapperTest {

    @Test
    void testParseWithFieldsMapping() throws IOException {
        Map<String, String> fieldsMapping = Map.ofEntries(
                Map.entry(ID, "id")
        );

        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("groups.json")) {
            String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            GroupResponseWrapper groupResponseWrapper = GroupResponseWrapper.fromJsonList(content, fieldsMapping);
            assertNotNull(groupResponseWrapper);
            List<Group> groups = groupResponseWrapper.getValue();
            assertEquals(5, groups.size());
            Optional<Group> johnDoe = groups.stream()
                    .filter(group -> group.getId().equals("d2901ae9-7624-4d1f-b8a1-123f9a7a14d5"))
                    .findFirst();
            Assertions.assertTrue(johnDoe.isPresent());
        }
    }
}
