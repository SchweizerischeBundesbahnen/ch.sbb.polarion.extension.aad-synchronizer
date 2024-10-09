package ch.sbb.polarion.extension.aad.synchronizer.connector;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class UrlBuilderTest {

    private static Stream<Arguments> testValuesWithoutPathArgs() {
        return Stream.of(
                Arguments.of("http://localhost:1080", GraphOption.GROUPS, "http://localhost:1080/v1.0/groups"),
                Arguments.of("http://localhost:1080", GraphOption.ORGANIZATION, "http://localhost:1080/v1.0/organization")
        );
    }

    @ParameterizedTest
    @MethodSource("testValuesWithoutPathArgs")
    void buildWithoutPathArguments(String baseUrl, GraphOption option, String expected) {

        UrlBuilder urlBuilder = new UrlBuilder();
        String url = urlBuilder.build(baseUrl, option);

        assertThat(url).isEqualTo(expected);
    }

    private static Stream<Arguments> testValuesWithPathArgs() {
        return Stream.of(
                Arguments.of("http://localhost:1080", GraphOption.GROUPS, "123124/member", "http://localhost:1080/v1.0/groups/123124/member"),
                Arguments.of("http://localhost:1080", GraphOption.ORGANIZATION, "123124/member", "http://localhost:1080/v1.0/organization/123124/member"),
                Arguments.of("http://localhost:1080", GraphOption.GROUPS, "123124", "http://localhost:1080/v1.0/groups/123124"),
                Arguments.of("http://localhost:1080", GraphOption.ORGANIZATION, "123124", "http://localhost:1080/v1.0/organization/123124"),
                Arguments.of("http://localhost:1080", GraphOption.GROUPS, "member", "http://localhost:1080/v1.0/groups/member"),
                Arguments.of("http://localhost:1080", GraphOption.ORGANIZATION, "member", "http://localhost:1080/v1.0/organization/member"),
                Arguments.of("http://localhost:1080", GraphOption.GROUPS, "", "http://localhost:1080/v1.0/groups"),
                Arguments.of("http://localhost:1080", GraphOption.ORGANIZATION, "", "http://localhost:1080/v1.0/organization"),
                Arguments.of("http://localhost:1080", GraphOption.GROUPS, null, "http://localhost:1080/v1.0/groups"),
                Arguments.of("http://localhost:1080", GraphOption.ORGANIZATION, null, "http://localhost:1080/v1.0/organization")
        );
    }

    @ParameterizedTest
    @MethodSource("testValuesWithPathArgs")
    void buildWithPathArguments(String baseUrl, GraphOption option, String pathArgs, String expected) {

        UrlBuilder urlBuilder = new UrlBuilder();
        String url = urlBuilder.build(baseUrl, option, pathArgs);

        assertThat(url).isEqualTo(expected);
    }
}
