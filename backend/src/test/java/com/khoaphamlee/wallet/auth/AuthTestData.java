package com.khoaphamlee.wallet.auth;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Random but valid user data, so tests sharing one database never collide with each other. */
public final class AuthTestData {

    private AuthTestData() {}

    public static String randomEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    /** A valid Vietnamese mobile number such as 0912345678. */
    public static String randomPhone() {
        return "09" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
    }
}
