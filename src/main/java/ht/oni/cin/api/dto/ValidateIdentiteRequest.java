package ht.oni.cin.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class ValidateIdentiteRequest {

    @NotBlank
    @Pattern(regexp = "\\d{10,13}", message = "Le NIN doit contenir 10 à 13 chiffres")
    @JsonAlias("nin")
    private String nin;

    @JsonAlias("nin_display")
    private String ninDisplay;

    @JsonAlias("numero_carte")
    private String numeroCarte;

    @NotBlank
    @Size(max = 100)
    private String nom;

    @NotBlank
    @Size(max = 200)
    private String prenom;

    @NotBlank
    @Pattern(regexp = "\\d{2}/\\d{2}/\\d{4}")
    @JsonAlias("date_naissance")
    private String dateNaissance;

    @NotBlank
    @JsonAlias("lieu_naissance")
    private String lieuNaissance;

    @NotBlank
    @Pattern(regexp = "[MF]")
    private String sexe;

    @Size(max = 10)
    private String nationalite;

    private String adresse;

    private String departement;

    @NotBlank
    @Pattern(regexp = "\\d{2}/\\d{2}/\\d{4}")
    @JsonAlias("date_emission")
    private String dateEmission;

    @NotBlank
    @Pattern(regexp = "\\d{2}/\\d{2}/\\d{4}")
    @JsonAlias("date_expiration")
    private String dateExpiration;

    private String photoBase64;
    private Double scoreOcrMoyen;

    @NotBlank
    private String idempotencyToken;

    private boolean consentementDocumente;
    private String baseLegale;

    private boolean confirmDuplicateCheck;
}
