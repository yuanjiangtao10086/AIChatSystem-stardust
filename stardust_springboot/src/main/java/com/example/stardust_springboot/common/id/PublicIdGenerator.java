package com.example.stardust_springboot.common.id;

import java.math.BigInteger;
import java.security.SecureRandom;

public final class PublicIdGenerator {

    private static final char[] CROCKFORD_BASE32 = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final BigInteger BASE = BigInteger.valueOf(32);

    private PublicIdGenerator() {
    }

    public static String newUlid() {
        byte[] value = new byte[16];
        long timestamp = System.currentTimeMillis();
        for (int index = 5; index >= 0; index--) {
            value[index] = (byte) timestamp;
            timestamp >>>= 8;
        }
        byte[] randomness = new byte[10];
        RANDOM.nextBytes(randomness);
        System.arraycopy(randomness, 0, value, 6, randomness.length);

        BigInteger number = new BigInteger(1, value);
        char[] encoded = new char[26];
        for (int index = encoded.length - 1; index >= 0; index--) {
            BigInteger[] quotientAndRemainder = number.divideAndRemainder(BASE);
            encoded[index] = CROCKFORD_BASE32[quotientAndRemainder[1].intValue()];
            number = quotientAndRemainder[0];
        }
        return new String(encoded);
    }
}
