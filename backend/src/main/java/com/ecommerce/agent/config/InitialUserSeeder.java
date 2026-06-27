package com.ecommerce.agent.config;

import com.ecommerce.agent.model.User;
import com.ecommerce.agent.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class InitialUserSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public InitialUserSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        upsertUser("user1", "123456", "user");
        upsertUser("admin", "1221jjc0", "admin");
    }

    private void upsertUser(String username, String rawPassword, String role) {
        if (userRepository.findByUsername(username).isPresent()) {
            return;
        }

        User user = new User(username, passwordEncoder.encode(rawPassword), role);
        userRepository.save(user);
    }
}
