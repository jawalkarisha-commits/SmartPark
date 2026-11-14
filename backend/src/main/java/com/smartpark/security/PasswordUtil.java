package com.smartpark.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

public class PasswordUtil {

    private static final int ITERATIONS = 210000;
    private static final int KEY_LENGTH = 256;
    private static final BCryptPasswordEncoder BCRYPT =
        new BCryptPasswordEncoder(12);

    public static String hashPassword(String password) {
        return BCRYPT.encode(password);
    }

    public static boolean isBcryptHash(String storedHash) {
        return storedHash != null
            && storedHash.matches("^\\$2[aby]\\$\\d{2}\\$.*");
    }

    public static boolean verifyPassword(
            String password, String storedHash) {
        if (isBcryptHash(storedHash)) {
            return new BCryptPasswordEncoder().matches(password, storedHash);
        }
        return verifyLegacyPbkdf2(password, storedHash);
    }

    public static boolean verifyLegacyPbkdf2(
            String password, String storedHash) {
        try {
            if (storedHash == null) {
                return false;
            }
            String[] parts = storedHash.split(":");

            if (parts.length != 2) {
                return false;
            }

            byte[] salt = Base64.getDecoder().decode(parts[0]);
            byte[] expectedHash = Base64.getDecoder().decode(parts[1]);

            PBEKeySpec spec = new PBEKeySpec(
                password.toCharArray(),
                salt,
                ITERATIONS,
                KEY_LENGTH
            );

            SecretKeyFactory factory =
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");

            byte[] actualHash = factory.generateSecret(spec).getEncoded();
            spec.clearPassword();

            return java.security.MessageDigest.isEqual(
                expectedHash, actualHash
            );

        } catch (Exception e) {
            return false;
        }
    }
}