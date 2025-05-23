package ch.sbb.polarion.extension.aad.synchronizer.model;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static ch.sbb.polarion.extension.aad.synchronizer.model.MemberResponseWrapper.*;
import static org.junit.jupiter.api.Assertions.*;

class MemberResponseWrapperTest {

    @Test
    void testParseWithFieldsMapping() throws IOException {
        Map<String, String> fieldsMapping = Map.ofEntries(
                Map.entry(ID, "mailNickname"),
                Map.entry(NAME, "displayName"),
                Map.entry(EMAIL, "mail")
        );

        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("members.json")) {
            String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            MemberResponseWrapper memberResponseWrapper = MemberResponseWrapper.fromJsonList(content, fieldsMapping);
            assertNotNull(memberResponseWrapper);
            List<Member> members = memberResponseWrapper.getValue();
            assertEquals(5, members.size());
            assertNull(memberResponseWrapper.getNextLink());
            Optional<Member> johnDoe = members.stream()
                    .filter(member -> member.getEmail().equals("john.doe@example.com"))
                    .findFirst();
            Assertions.assertTrue(johnDoe.isPresent());
            assertEquals("jdoe", johnDoe.get().getId());
            assertEquals("John Doe", johnDoe.get().getName());
        }
    }

    @Test
    void testParseNextLink() throws IOException {
        Map<String, String> fieldsMapping = Map.ofEntries(
                Map.entry(ID, "mailNickname"),
                Map.entry(NAME, "displayName"),
                Map.entry(EMAIL, "mail")
        );

        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("membersPaginated.json")) {
            String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            MemberResponseWrapper memberResponseWrapper = MemberResponseWrapper.fromJsonList(content, fieldsMapping);
            assertNotNull(memberResponseWrapper);
            assertEquals("http://localhost:1080/v1.0/groups/next/members", memberResponseWrapper.getNextLink());
        }
    }
}
