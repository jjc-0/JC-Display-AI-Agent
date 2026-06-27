package com.ecommerce.agent.config;

import com.ecommerce.agent.model.User;
import com.ecommerce.agent.model.ConversationRecord;
import com.ecommerce.agent.model.ConversationSession;
import com.ecommerce.agent.repository.ConversationRecordRepository;
import com.ecommerce.agent.repository.ConversationSessionRepository;
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
    private final ConversationSessionRepository sessionRepository;
    private final ConversationRecordRepository recordRepository;

    public UserAccountMigration(UserRepository userRepository,
                                ConversationSessionRepository sessionRepository,
                                ConversationRecordRepository recordRepository) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.recordRepository = recordRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        long totalUsers = userRepository.count();
        if (totalUsers == 0 || userRepository.countByEnabledTrue() > 0) {
            migrateConversationOwners();
            return;
        }

        for (User user : userRepository.findAll()) {
            user.setEnabled(true);
            userRepository.save(user);
        }
        log.warn("Recovered {} existing users to enabled=true after account status migration.", totalUsers);
        migrateConversationOwners();
    }

    private void migrateConversationOwners() {
        String fallbackUsername = userRepository.findByUsername("admin")
                .map(User::getUsername)
                .orElseGet(() -> userRepository.findAll().stream().findFirst().map(User::getUsername).orElse("user"));

        for (ConversationSession session : sessionRepository.findAll()) {
            if (session.getUsername() == null || session.getUsername().isBlank()) {
                session.setUsername(fallbackUsername);
                session.setUserId(fallbackUsername);
                sessionRepository.save(session);
            }
        }

        for (ConversationRecord record : recordRepository.findAll()) {
            if (record.getUsername() == null || record.getUsername().isBlank()) {
                record.setUsername(fallbackUsername);
                record.setUserId(fallbackUsername);
                recordRepository.save(record);
            }
        }
    }
}
