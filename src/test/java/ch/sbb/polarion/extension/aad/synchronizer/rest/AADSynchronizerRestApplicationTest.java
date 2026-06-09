package ch.sbb.polarion.extension.aad.synchronizer.rest;

import ch.sbb.polarion.extension.generic.rest.GenericRestApplication;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AADSynchronizerRestApplicationTest {

    @Test
    void instantiatesAsGenericRestApplication() {
        AADSynchronizerRestApplication application = new AADSynchronizerRestApplication();

        assertThat(application).isInstanceOf(GenericRestApplication.class);
    }
}
