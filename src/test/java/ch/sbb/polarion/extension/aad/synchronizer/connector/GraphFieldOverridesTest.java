package ch.sbb.polarion.extension.aad.synchronizer.connector;

import ch.sbb.polarion.extension.aad.synchronizer.model.MemberResponseWrapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class GraphFieldOverridesTest {

    @Test
    void canonicalConstructorNormalizesNullAndBlankToNull() {
        GraphFieldOverrides allBlank = new GraphFieldOverrides(null, "", "   ");
        assertThat(allBlank.idField()).isNull();
        assertThat(allBlank.nameField()).isNull();
        assertThat(allBlank.emailField()).isNull();
    }

    @Test
    void canonicalConstructorTrimsNonBlankValues() {
        GraphFieldOverrides padded = new GraphFieldOverrides("  onPremisesSamAccountName  ", "\tdisplayName\n", "mail");
        assertThat(padded.idField()).isEqualTo("onPremisesSamAccountName");
        assertThat(padded.nameField()).isEqualTo("displayName");
        assertThat(padded.emailField()).isEqualTo("mail");
    }

    @Test
    void emptyConstantHasAllFieldsNull() {
        assertThat(GraphFieldOverrides.EMPTY.idField()).isNull();
        assertThat(GraphFieldOverrides.EMPTY.nameField()).isNull();
        assertThat(GraphFieldOverrides.EMPTY.emailField()).isNull();
        assertThat(GraphFieldOverrides.EMPTY.asMap()).isEmpty();
    }

    @Test
    void asMapContainsOnlyPresentFields() {
        GraphFieldOverrides partial = new GraphFieldOverrides("onPremisesSamAccountName", null, "mail");
        assertThat(partial.asMap()).containsOnly(
                entry(MemberResponseWrapper.ID, "onPremisesSamAccountName"),
                entry(MemberResponseWrapper.EMAIL, "mail"));
    }

    @Test
    void asMapIsImmutable() {
        GraphFieldOverrides overrides = new GraphFieldOverrides("a", "b", "c");
        assertThat(overrides.asMap()).isUnmodifiable();
    }
}
