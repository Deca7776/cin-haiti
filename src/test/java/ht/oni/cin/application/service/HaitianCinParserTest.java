package ht.oni.cin.application.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HaitianCinParserTest {

    private final HaitianCinParser parser = new HaitianCinParser();

    @Test
    void parse_patriciaDelaireSample() {
        String ocrText = """
                REPUBLIQUE D'HAITI
                CARTE D'IDENTIFICATION NATIONALE
                Numéro de carte / Nimewo kat la
                T1K2N89G7
                Prénom / Non
                PATRICIA
                Nom / Siyati
                DELAIRE
                Sexe / Sèks
                F
                Nationalité / Nasyonalite
                Haïtien / Ayisyen
                Date de Naissance / Dat ou fèt
                01-01-1987
                Lieu de Naissance / Kote ou fèt
                Département Ouest, Commune Port-au-Prince
                Date d'émission / Dat kat la fèt
                07-02-2018
                Date d'expiration / Dat kat la fini
                06-02-2028
                Numéro d'identification unique / Nimewo idantifikasyon inik
                0123456789
                """;

        var fields = parser.parse(ocrText, 80);

        assertEquals("T1K2N89G7", fields.get("numero_carte").getValue());
        assertEquals("PATRICIA", fields.get("prenom").getValue());
        assertEquals("DELAIRE", fields.get("nom").getValue());
        assertEquals("F", fields.get("sexe").getValue());
        assertEquals("HTI", fields.get("nationalite").getValue());
        assertEquals("01/01/1987", fields.get("date_naissance").getValue());
        assertEquals("07/02/2018", fields.get("date_emission").getValue());
        assertEquals("06/02/2028", fields.get("date_expiration").getValue());
        assertEquals("0123456789", fields.get("nin_display").getValue());
        assertEquals("0000123456789", fields.get("nin").getValue());
        assertTrue(fields.get("lieu_naissance").getValue().contains("Port-au-Prince"));
        assertEquals("Ouest", fields.get("departement").getValue());
    }

    @Test
    void parse_noisyOcrSample() {
        String ocrText = """
                : QUE D'HAITI Numéro de carte / Nirmiewo kat la YITI D T1K2N89G7
                Prénom / Noë ++. Sexe / Sèks PATRICI
                Nom / Siyati Hartien 7 Ayisyen
                01-01-1987 Naissance / Kote ou fêt sr Ouest, Commune Port-au-Prince
                Date d'expiration / Dat kat la fini -07-02-2018 06-02-2028
                RNCS = 0123456789
                """;

        var fields = parser.parse(ocrText, 80);

        assertEquals("T1K2N89G7", fields.get("numero_carte").getValue());
        assertTrue(fields.get("prenom").getValue().contains("PATRIC"));
        assertEquals("0123456789", fields.get("nin_display").getValue());
        assertEquals("HTI", fields.get("nationalite").getValue());
        assertTrue(fields.get("lieu_naissance").getValue().contains("Port-au-Prince"));
        assertEquals("07/02/2018", fields.get("date_emission").getValue());
        assertEquals("06/02/2028", fields.get("date_expiration").getValue());
    }
}
