package ch.sbb.polarion.extension.aad.synchronizer.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrganizationData {
    private Instant onPremisesLastSyncDateTime;
}
