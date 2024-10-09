package ch.sbb.polarion.extension.aad.synchronizer.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrganizationDataWrapper {

    private List<OrganizationData> value;
}
