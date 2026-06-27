package com.ecommerce.agent.config;

import com.ecommerce.agent.model.User;
import com.ecommerce.agent.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class UserAccountMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UserAccountMigration.class);

    private final UserRepository userRepository;

    public UserAccountMigration(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        long totalUsers = userRepository.count();
        if (totalUsers == 0 || userRepository.countByEnabledTrue() > 0) {
            return;
        }

        for (User user : userRepository.findAll()) {
            user.setEnabled(true);
            userRepository.save(user);
        }
        log.warn("Recovered {} existing users to enabled=true after account status migration.", totalUsers);
    }
}
