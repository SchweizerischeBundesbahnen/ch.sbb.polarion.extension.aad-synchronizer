package ch.sbb.polarion.extension.aad.synchronizer.rest;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAPIInfoTest {

    @Test
    void instantiatesAndCarriesOpenAPIDefinition() {
        assertThat(new OpenAPIInfo()).isNotNull();

        OpenAPIDefinition definition = OpenAPIInfo.class.getAnnotation(OpenAPIDefinition.class);
        assertThat(definition).isNotNull();
        assertThat(definition.info().title()).isEqualTo("AAD Synchronizer REST API");
        assertThat(definition.info().version()).isEqualTo("v1");
    }
}
