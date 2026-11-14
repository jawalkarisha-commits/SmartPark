package com.smartpark;

import com.smartpark.security.PasswordUtil;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordUtilTest {

    @Test
    void newPasswordHashesUseBcrypt() {
        String hash = PasswordUtil.hashPassword("SmartPark1");

        assertTrue(PasswordUtil.isBcryptHash(hash));
        assertTrue(PasswordUtil.verifyPassword("SmartPark1", hash));
        assertFalse(PasswordUtil.verifyPassword("WrongPassword1", hash));
    }

    @Test
    void verifiesExistingPbkdf2HashSoSuccessfulLoginCanUpgradeIt() throws Exception {
        String hash = legacyPbkdf2Hash("SmartPark1");

        assertFalse(PasswordUtil.isBcryptHash(hash));
        assertTrue(PasswordUtil.verifyPassword("SmartPark1", hash));
        assertFalse(PasswordUtil.verifyPassword("WrongPassword1", hash));
    }

    private static String legacyPbkdf2Hash(String password) throws Exception {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        PBEKeySpec spec = new PBEKeySpec(
            password.toCharArray(), salt, 210000, 256
        );
        byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec)
            .getEncoded();
        spec.clearPassword();
        return Base64.getEncoder().encodeToString(salt)
            + ":"
            + Base64.getEncoder().encodeToString(hash);
    }
}
