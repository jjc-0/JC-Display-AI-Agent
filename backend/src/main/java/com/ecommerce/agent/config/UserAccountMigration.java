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
        for (ConversationSession session : sessionRepository.findAll()) {
            User owner = resolveOwner(session.getUserId(), session.getUsername());
            if (owner != null) {
                session.setUserId(String.valueOf(owner.getId()));
                session.setUsername(owner.getUsername());
                sessionRepository.save(session);
            } else if (hasLegacyOwnerHint(session.getUserId(), session.getUsername())) {
                session.setUserId(null);
                session.setUsername(null);
                sessionRepository.save(session);
            }
        }

        for (ConversationRecord record : recordRepository.findAll()) {
            User owner = resolveOwner(record.getUserId(), record.getUsername());
            if (owner != null) {
                record.setUserId(String.valueOf(owner.getId()));
                record.setUsername(owner.getUsername());
                recordRepository.save(record);
            } else if (hasLegacyOwnerHint(record.getUserId(), record.getUsername())) {
                record.setUserId(null);
                record.setUsername(null);
                recordRepository.save(record);
            }
        }
    }

    private User resolveOwner(String userId, String username) {
        if (userId != null && userId.matches("^\\d+$")) {
            return userRepository.findById(Long.parseLong(userId)).orElse(null);
        }
        if (username != null && !username.isBlank()) {
            return userRepository.findByUsername(username).orElse(null);
        }
        if (userId != null && !userId.isBlank()) {
            return userRepository.findByUsername(userId).orElse(null);
        }
        return null;
    }

    private boolean hasLegacyOwnerHint(String userId, String username) {
        return (userId != null && !userId.isBlank() && !userId.matches("^\\d+$"))
                || (userId == null || userId.isBlank()) && username != null && !username.isBlank();
    }
}
