package ch.sbb.polarion.extension.aad.synchronizer.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrganizationData {
    private Date onPremisesLastSyncDateTime;
}
