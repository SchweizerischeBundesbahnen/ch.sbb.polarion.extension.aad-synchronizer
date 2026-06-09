package ch.sbb.polarion.extension.aad.synchronizer.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CollectionUtilsTest {

    @Test
    void nullListYieldsEmptyString() {
        assertThat(CollectionUtils.usersAsString(null)).isEmpty();
    }

    @Test
    void emptyListYieldsEmptyString() {
        assertThat(CollectionUtils.usersAsString(List.of())).isEmpty();
    }

    @Test
    void singleUserIsQuotedWithoutBrackets() {
        assertThat(CollectionUtils.usersAsString(List.of("alice"))).isEqualTo("'alice'");
    }

    @Test
    void multipleUsersAreQuotedAndBracketed() {
        assertThat(CollectionUtils.usersAsString(List.of("alice", "bob", "carol")))
                .isEqualTo("['alice', 'bob', 'carol']");
    }
}
