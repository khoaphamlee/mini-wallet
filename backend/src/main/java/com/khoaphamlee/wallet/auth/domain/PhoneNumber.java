package com.khoaphamlee.wallet.auth.domain;

import java.util.regex.Pattern;

/** Vietnamese mobile numbers, always stored in the {@code +84} form. */
public final class PhoneNumber {

    /** {@code 0}, {@code 84} or {@code +84}, then 3, 5, 7, 8 or 9, then 8 more digits. */
    public static final String PATTERN = "^(0|84|\\+84)[35789]\\d{8}$";

    private static final Pattern COMPILED = Pattern.compile(PATTERN);

    private PhoneNumber() {}

    /** Returns the number in {@code +84} form: {@code 0901234567} becomes {@code +84901234567}. */
    public static String normalize(String raw) {
        String phone = raw.strip();
        if (!COMPILED.matcher(phone).matches()) {
            throw new IllegalArgumentException("Not a Vietnamese mobile number: " + raw);
        }
        return "+84" + phone.substring(phone.length() - 9);
    }
}
