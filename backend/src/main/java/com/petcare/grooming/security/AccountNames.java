package com.petcare.grooming.security;

import java.util.Locale;

public final class AccountNames {
    private AccountNames() {}

    public static String normalize(String username) {
        if (username == null) return "";
        String value = username.trim();
        return value.indexOf('@') >= 0 ? value.toLowerCase(Locale.ROOT) : value;
    }

    public static boolean valid(String username) {
        if (username == null || username.isEmpty() || username.length() > 64) return false;
        if (username.matches("[A-Za-z0-9_]{3,32}")) return true;
        return username.matches("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    }
}
