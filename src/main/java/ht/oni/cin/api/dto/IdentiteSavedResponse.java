package ht.oni.cin.api.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class IdentiteSavedResponse {
    private UUID id;
    private String nin;
    private String ninDisplay;
    private String numeroCarte;
    private String prenom;
    private String nom;
    private String message;
    private String nextStep;
}
