package com.smartpark.security;

import java.util.regex.Pattern;

public final class PasswordPolicy {

    public static final String MESSAGE =
        "Password must be at least 6 characters and include an uppercase letter and a number";

    private static final Pattern VALID_PASSWORD =
        Pattern.compile("^(?=.*[A-Z])(?=.*\\d).{6,}$");

    private PasswordPolicy() {
    }

    public static boolean isValid(String password) {
        return password != null && VALID_PASSWORD.matcher(password).matches();
    }

    public static void validate(String password) {
        if (!isValid(password)) {
            throw new IllegalArgumentException(MESSAGE);
        }
    }
}
