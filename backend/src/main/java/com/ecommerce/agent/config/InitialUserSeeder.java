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
        if (userRepository.count() == 0) {
            createInitialAdmin();
        }
    }

    private void createInitialAdmin() {
        User user = new User("admin", passwordEncoder.encode("1221jjc0"), "admin");
        userRepository.save(user);
    }
}
