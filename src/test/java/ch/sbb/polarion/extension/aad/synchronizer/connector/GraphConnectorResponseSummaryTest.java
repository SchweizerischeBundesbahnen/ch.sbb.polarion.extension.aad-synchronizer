package ch.sbb.polarion.extension.aad.synchronizer.connector;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-level coverage of the compact log summarizer used when {@code verboseGraphLog=false}.
 * Exercises the static {@code GraphConnector#summarizeJsonResponse} helper directly — the
 * connector's WireMock end-to-end coverage in {@link GraphConnectorTest} would equally exercise
 * it but at much higher overhead per assertion.
 *
 * <p>Documents the shapes the summarizer must handle without exploding the job log:
 * <ul>
 *   <li>collection responses ({@code value} array) — count header + one line per entity, capped
 *       at {@code MAX_SUMMARY_ENTRIES} with a "+N more" suffix;</li>
 *   <li>paginated responses ({@code @odata.nextLink}) — count header annotated with
 *       "(paginated)" so the operator knows there's more behind the cursor;</li>
 *   <li>single-entity responses (e.g. {@code /organization}) — full pretty-printed JSON because
 *       the payload is inherently small;</li>
 *   <li>malformed/non-JSON bodies — truncated raw dump as a diagnostic fallback.</li>
 * </ul>
 */
class GraphConnectorResponseSummaryTest {

    @Test
    void collectionWithSingleEntityRendersHeaderAndOneLine() {
        String body = """
                {
                    "@odata.context": "...",
                    "value": [
                        {"id": "55073b31", "displayName": "TEST_AAD_SYNC_polarion"}
                    ]
                }
                """;

        String summary = GraphConnector.summarizeJsonResponse(body);

        assertThat(summary)
                .startsWith("1 entity:")
                .contains("id=\"55073b31\"")
                .contains("displayName=\"TEST_AAD_SYNC_polarion\"");
    }

    @Test
    void collectionWithMultipleEntitiesRendersOneLinePerEntry() {
        String body = """
                {
                    "value": [
                        {"id": "g1", "displayName": "A"},
                        {"id": "g2", "displayName": "B"}
                    ]
                }
                """;

        String summary = GraphConnector.summarizeJsonResponse(body);

        assertThat(summary).startsWith("2 entities:");
        assertThat(summary.lines().count())
                .as("one header line + one line per entity")
                .isEqualTo(3);
        assertThat(summary).contains("displayName=\"A\"").contains("displayName=\"B\"");
    }

    @Test
    void usersResponsePicksUpEmployeeIdAndMail() {
        // Mirrors the real /groups/{id}/members projection used by the job: id + employeeId
        // (when graphIdField=employeeId) + displayName + mail. Pins that the summarizer surfaces
        // each of those fields rather than collapsing to a generic id-only summary. Values are
        // intentionally synthetic placeholders (no real-looking employee IDs or names) so the
        // pre-commit sensitive-data-leak hook stays green.
        String body = """
                {
                    "value": [
                        {"id": "aad-1", "employeeId": "EMPLOYEE_ID_1", "displayName": "Test User One", "mail": "user.one@example.com"},
                        {"id": "aad-2", "employeeId": "EMPLOYEE_ID_2", "displayName": "Test User Two", "mail": "user.two@example.com"}
                    ]
                }
                """;

        String summary = GraphConnector.summarizeJsonResponse(body);

        assertThat(summary)
                .contains("employeeId=\"EMPLOYEE_ID_1\"")
                .contains("mail=\"user.one@example.com\"")
                .contains("employeeId=\"EMPLOYEE_ID_2\"")
                .contains("mail=\"user.two@example.com\"");
    }

    @Test
    void largeCollectionIsCappedWithMoreSuffix() {
        // Build a 25-entity body so the cap (20) trims and the +N-more suffix fires. The exact
        // cap is intentionally not asserted here — it's a tunable; the contract is that very
        // large collections never emit one line per entity.
        StringBuilder body = new StringBuilder("{\"value\":[");
        for (int i = 0; i < 25; i++) {
            if (i > 0) {
                body.append(',');
            }
            body.append("{\"id\":\"g").append(i).append("\",\"displayName\":\"name").append(i).append("\"}");
        }
        body.append("]}");

        String summary = GraphConnector.summarizeJsonResponse(body.toString());

        assertThat(summary).startsWith("25 entities:");
        assertThat(summary)
                .as("the +N-more suffix must point operators at the verbose toggle")
                .contains("... +5 more")
                .contains("verboseGraphLog");
        // The first entry must be present; the last entry beyond the cap must NOT be inlined.
        assertThat(summary).contains("displayName=\"name0\"");
        assertThat(summary).doesNotContain("displayName=\"name24\"");
    }

    @Test
    void paginatedCollectionAnnotatesHeader() {
        String body = """
                {
                    "@odata.nextLink": "https://graph.microsoft.com/v1.0/groups?$skiptoken=...",
                    "value": [
                        {"id": "g1", "displayName": "A"}
                    ]
                }
                """;

        String summary = GraphConnector.summarizeJsonResponse(body);

        assertThat(summary).contains("(paginated)");
    }

    @Test
    void emptyCollectionRendersHeaderOnly() {
        String body = "{\"value\": []}";

        String summary = GraphConnector.summarizeJsonResponse(body);

        assertThat(summary).isEqualTo("0 entities");
    }

    @Test
    void singleEntityResponseKeepsFullPrettyPrintedJson() {
        // Responses without a value array (e.g. /organization, /users/{id}) are kept full —
        // those payloads are bounded in size and the entire JSON is the most useful view.
        String body = "{\"id\": \"org-1\", \"displayName\": \"Contoso\", \"verifiedDomains\": [{\"name\": \"contoso.com\"}]}";

        String summary = GraphConnector.summarizeJsonResponse(body);

        // Pretty-printed → contains a newline immediately after the leading separator, plus
        // every field of the original payload (including nested arrays).
        assertThat(summary).startsWith(System.lineSeparator() + "{");
        assertThat(summary)
                .contains("\"displayName\": \"Contoso\"")
                .contains("\"verifiedDomains\"")
                .contains("\"contoso.com\"");
    }

    @Test
    void malformedBodyFallsBackToTruncatedRaw() {
        // A 5KB string with no JSON shape: the summarizer must not throw — it returns a
        // truncated raw dump pointing at the verbose toggle so the operator still has a
        // diagnostic anchor.
        String body = "x".repeat(5_000);

        String summary = GraphConnector.summarizeJsonResponse(body);

        assertThat(summary)
                .as("malformed body must be truncated, not emitted in full")
                .startsWith("xxxx")
                .contains("more chars")
                .contains("verboseGraphLog");
        assertThat(summary.length()).isLessThan(body.length());
    }

    @Test
    void shortMalformedBodyIsReturnedVerbatim() {
        // The truncation logic must only kick in past the cap; short non-JSON bodies are
        // emitted verbatim so the operator sees exactly what came back.
        String body = "<html>service unavailable</html>";

        String summary = GraphConnector.summarizeJsonResponse(body);

        assertThat(summary).isEqualTo(body);
    }
}
