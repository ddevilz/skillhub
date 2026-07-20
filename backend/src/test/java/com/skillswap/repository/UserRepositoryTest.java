package com.skillswap.repository;

import com.skillswap.entity.Role;
import com.skillswap.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired UserRepository userRepository;

    @Test
    void savesAndFindsByEmail() {
        User u = new User();
        u.setFullName("Deva");
        u.setEmail("deva@example.com");
        u.setPasswordHash("hash");
        u.setRole(Role.USER);
        u.setActive(true);
        userRepository.save(u);

        assertThat(userRepository.findByEmail("deva@example.com")).isPresent();
        assertThat(userRepository.existsByEmail("deva@example.com")).isTrue();
        assertThat(userRepository.existsByEmail("nope@example.com")).isFalse();
    }
}
