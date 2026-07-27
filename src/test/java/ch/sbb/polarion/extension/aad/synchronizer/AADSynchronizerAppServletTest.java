package ch.sbb.polarion.extension.aad.synchronizer;

import ch.sbb.polarion.extension.generic.GenericUiServlet;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AADSynchronizerAppServletTest {

    @Test
    void instantiatesAsGenericUiServlet() {
        AADSynchronizerAppServlet servlet = new AADSynchronizerAppServlet();

        assertThat(servlet).isInstanceOf(GenericUiServlet.class);
    }
}
