package ch.sbb.polarion.extension.aad.synchronizer;

import ch.sbb.polarion.extension.generic.GenericUiServlet;

import java.io.Serial;

public class AADSynchronizerAdminUiServlet extends GenericUiServlet {

    @Serial
    private static final long serialVersionUID = -1960162496576219706L;

    public AADSynchronizerAdminUiServlet() {
        super("aad-synchronizer-admin");
    }
}
