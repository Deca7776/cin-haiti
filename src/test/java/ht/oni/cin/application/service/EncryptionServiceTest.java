package ht.oni.cin.application.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EncryptionServiceTest {

    private final EncryptionService encryptionService = new EncryptionService(createProperties());

    @Test
    void encryptDecrypt_roundTrip() {
        String plain = "NIN:1234567890123|Nom:DUPONT";
        String encrypted = encryptionService.encrypt(plain);
        assertNotEquals(plain, encrypted);
        assertEquals(plain, encryptionService.decrypt(encrypted));
    }

    @Test
    void sha256_isDeterministic() {
        String hash1 = encryptionService.sha256("test");
        String hash2 = encryptionService.sha256("test");
        assertEquals(hash1, hash2);
        assertEquals(64, hash1.length());
    }

    private static ht.oni.cin.config.CinProperties createProperties() {
        ht.oni.cin.config.CinProperties props = new ht.oni.cin.config.CinProperties();
        props.getEncryption().setAesKey("0123456789ABCDEF0123456789ABCDEF");
        return props;
    }
}
