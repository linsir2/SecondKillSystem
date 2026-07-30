package com.seckill;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

class PasswordHashGenTest {
    @Test
    void printHashes() {
        Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(16, 32, 1, 65536, 3);
        String[] passwords = {"123456"};
        for (String pw : passwords) {
            System.out.println("HASH:" + encoder.encode(pw));
        }
    }
}
