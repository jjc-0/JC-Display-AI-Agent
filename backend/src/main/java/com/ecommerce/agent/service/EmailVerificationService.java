package com.ecommerce.agent.service;

import com.ecommerce.agent.model.EmailVerificationCode;
import com.ecommerce.agent.repository.EmailVerificationCodeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class EmailVerificationService {

    public static final String PURPOSE_LOGIN = "login";
    public static final String PURPOSE_RESET_PASSWORD = "reset_password";
    public static final String PURPOSE_BIND_QQ = "bind_qq";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final EmailVerificationCodeRepository codeRepository;
    private final JavaMailSender mailSender;
    private final PasswordEncoder passwordEncoder;
    private final String mailUsername;

    public EmailVerificationService(EmailVerificationCodeRepository codeRepository,
                                    JavaMailSender mailSender,
                                    PasswordEncoder passwordEncoder,
                                    @Value("${spring.mail.username:}") String mailUsername) {
        this.codeRepository = codeRepository;
        this.mailSender = mailSender;
        this.passwordEncoder = passwordEncoder;
        this.mailUsername = mailUsername;
    }

    public void sendCode(String email, String purpose) {
        String normalizedEmail = normalizeEmail(email);
        if (mailUsername == null || mailUsername.isBlank()) {
            throw new IllegalStateException("QQ 邮箱 SMTP 未配置，请设置 MAIL_USERNAME 和 MAIL_PASSWORD");
        }

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        EmailVerificationCode entity = new EmailVerificationCode();
        entity.setTargetEmail(normalizedEmail);
        entity.setPurpose(purpose);
        entity.setCodeHash(passwordEncoder.encode(code));
        entity.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        codeRepository.save(entity);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailUsername);
        message.setTo(normalizedEmail);
        message.setSubject(subjectFor(purpose));
        message.setText("您的验证码是：" + code + "\n\n验证码 10 分钟内有效。如非本人操作，请忽略本邮件。");
        mailSender.send(message);
    }

    public boolean verify(String email, String purpose, String code) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedCode = code == null ? "" : code.trim();
        if (normalizedCode.length() != 6) return false;

        return codeRepository
                .findFirstByTargetEmailAndPurposeAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
                        normalizedEmail,
                        purpose,
                        LocalDateTime.now()
                )
                .filter(entity -> passwordEncoder.matches(normalizedCode, entity.getCodeHash()))
                .map(entity -> {
                    entity.setUsed(true);
                    codeRepository.save(entity);
                    return true;
                })
                .orElse(false);
    }

    public String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    public boolean isQqEmail(String email) {
        return normalizeEmail(email).matches("^[a-zA-Z0-9._%+-]+@qq\\.com$");
    }

    private String subjectFor(String purpose) {
        return switch (purpose) {
            case PURPOSE_LOGIN -> "JC Display 登录验证码";
            case PURPOSE_RESET_PASSWORD -> "JC Display 找回密码验证码";
            case PURPOSE_BIND_QQ -> "JC Display QQ 邮箱绑定验证码";
            default -> "JC Display 验证码";
        };
    }
}
