package com.smartpark;

import com.smartpark.security.PasswordPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordPolicyTest {

    @Test
    void acceptsSixCharactersWithUppercaseAndDigit() {
        assertTrue(PasswordPolicy.isValid("Abcde1"));
    }

    @Test
    void rejectsPasswordShorterThanSixCharacters() {
        assertFalse(PasswordPolicy.isValid("Abc1"));
    }

    @Test
    void rejectsPasswordWithoutUppercase() {
        assertFalse(PasswordPolicy.isValid("abcdef1"));
    }

    @Test
    void rejectsPasswordWithoutNumber() {
        assertFalse(PasswordPolicy.isValid("Abcdefg"));
    }
}
