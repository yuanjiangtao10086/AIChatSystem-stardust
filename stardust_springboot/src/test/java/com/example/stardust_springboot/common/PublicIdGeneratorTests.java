package com.example.stardust_springboot.common;

import com.example.stardust_springboot.common.id.PublicIdGenerator;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PublicIdGeneratorTests {

    @Test
    void generatesUniqueCrockfordBase32Ulids() {
        Set<String> ids = new HashSet<>();

        for (int index = 0; index < 1_000; index++) {
            ids.add(PublicIdGenerator.newUlid());
        }

        assertThat(ids).hasSize(1_000).allMatch(id -> id.matches("[0-9A-HJKMNP-TV-Z]{26}"));
    }
}
