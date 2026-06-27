package com.ecommerce.agent.service;

import com.ecommerce.agent.model.EmailVerificationCode;
import com.ecommerce.agent.repository.EmailVerificationCodeRepository;
import jakarta.annotation.PreDestroy;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);

    public static final String PURPOSE_LOGIN = "login";
    public static final String PURPOSE_REGISTER = "register";
    public static final String PURPOSE_RESET_PASSWORD = "reset_password";
    public static final String PURPOSE_BIND_QQ = "bind_qq";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String LOGO_BLUE_CID = "jcLeafBlue";
    private static final String LOGO_GREEN_CID = "jcLeafGreen";

    private final EmailVerificationCodeRepository codeRepository;
    private final JavaMailSender mailSender;
    private final PasswordEncoder passwordEncoder;
    private final String mailUsername;
    private final ExecutorService mailExecutor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "email-verification-sender");
        thread.setDaemon(true);
        return thread;
    });

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

        mailExecutor.submit(() -> {
            try {
                sendHtmlMail(normalizedEmail, purpose, code);
            } catch (Exception e) {
                log.error("验证码邮件后台发送失败 email={} purpose={}", normalizedEmail, purpose, e);
            }
        });
    }

    public boolean verify(String email, String purpose, String code) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedCode = code == null ? "" : code.trim();
        if (!normalizedCode.matches("^\\d{6}$")) return false;

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

    @PreDestroy
    public void shutdownMailExecutor() {
        mailExecutor.shutdown();
        try {
            if (!mailExecutor.awaitTermination(8, TimeUnit.SECONDS)) {
                mailExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            mailExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void sendHtmlMail(String email, String purpose, String code) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(mailUsername);
        helper.setTo(email);
        helper.setSubject(subjectFor(purpose));
        helper.setText(textContent(purpose, code), htmlContent(purpose, code));

        File blueLeaf = requireAsset("logo-leaf-blue.png");
        File greenLeaf = requireAsset("logo-leaf-green.png");
        helper.addInline(LOGO_BLUE_CID, new FileSystemResource(blueLeaf));
        helper.addInline(LOGO_GREEN_CID, new FileSystemResource(greenLeaf));

        mailSender.send(message);
    }

    private File requireAsset(String filename) {
        File asset = findAsset(filename);
        if (asset == null) {
            throw new IllegalStateException("邮件 Logo 资源未找到：" + filename);
        }
        return asset;
    }

    private File findAsset(String filename) {
        Path cwd = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        List<Path> candidates = new ArrayList<>();
        candidates.add(cwd.resolve("frontend/public").resolve(filename));
        candidates.add(cwd.resolve("../frontend/public").resolve(filename));
        candidates.add(cwd.resolve("backend/src/main/resources/static").resolve(filename));
        candidates.add(cwd.resolve("src/main/resources/static").resolve(filename));

        for (Path candidate : candidates) {
            Path normalized = candidate.normalize();
            if (Files.isRegularFile(normalized)) {
                return normalized.toFile();
            }
        }
        return null;
    }

    private String textContent(String purpose, String code) {
        return """
                JC Display AI 验证码

                用途：%s
                验证码：%s

                验证码 10 分钟内有效，请勿向任何人透露。
                如果不是您本人操作，请忽略本邮件。

                Shenzhen JC Display Packaging Co., Ltd. 保留全部权利。
                """.formatted(actionFor(purpose), code);
    }

    private String htmlContent(String purpose, String code) {
        String action = actionFor(purpose);
        String leafLogo = """
                <table role="presentation" cellspacing="0" cellpadding="0" style="border-collapse:collapse;background:transparent;">
                  <tr>
                    <td style="width:68px;height:50px;padding:0;background:transparent;vertical-align:middle;">
                      <table role="presentation" cellspacing="0" cellpadding="0" style="border-collapse:collapse;background:transparent;">
                        <tr>
                          <td style="padding:14px 0 0 2px;background:transparent;vertical-align:top;">
                            <img src="cid:%s" width="30" style="display:block;width:30px;height:auto;border:0;outline:none;text-decoration:none;background:transparent;" alt="" />
                          </td>
                          <td style="padding:0 0 0 2px;background:transparent;vertical-align:top;">
                            <img src="cid:%s" width="42" style="display:block;width:42px;height:auto;border:0;outline:none;text-decoration:none;background:transparent;" alt="" />
                          </td>
                        </tr>
                      </table>
                    </td>
                  </tr>
                </table>
                """.formatted(LOGO_BLUE_CID, LOGO_GREEN_CID);

        return """
                <!doctype html>
                <html>
                <body style="margin:0;padding:0;background:#F4F7F5;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Arial,'Microsoft YaHei',sans-serif;color:#17211F;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#F4F7F5;padding:32px 16px;">
                    <tr>
                      <td align="center">
                        <table role="presentation" width="620" cellspacing="0" cellpadding="0" style="max-width:620px;width:100%%;border-collapse:separate;border-spacing:0;">
                          <tr>
                            <td style="padding:0 0 14px 0;">
                              <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="border-collapse:collapse;">
                                <tr>
                                  <td align="left" style="vertical-align:middle;">
                                    <table role="presentation" cellspacing="0" cellpadding="0" style="border-collapse:collapse;">
                                      <tr>
                                        <td style="vertical-align:middle;padding:0 10px 0 0;">%s</td>
                                        <td style="vertical-align:middle;padding:0;white-space:nowrap;">
                                          <div style="font-size:27px;line-height:1;font-weight:900;letter-spacing:-0.03em;color:#139CDA;">JC</div>
                                          <div style="margin-top:5px;font-size:17px;line-height:1;font-weight:900;letter-spacing:-0.02em;color:#17211F;">DisplayPackaging</div>
                                          <div style="margin-top:6px;font-size:11px;line-height:1.2;font-weight:800;color:#64726B;">杰创展示 &amp; 包装</div>
                                        </td>
                                      </tr>
                                    </table>
                                  </td>
                                  <td align="right" style="vertical-align:middle;padding-left:18px;">
                                    <div style="display:inline-block;padding:7px 10px;border:1px solid #D7E8E0;border-radius:999px;background:#FFFFFF;color:#1F5F53;font-size:11px;font-weight:900;letter-spacing:0.1em;white-space:nowrap;">AI EXPORT CONSOLE</div>
                                  </td>
                                </tr>
                              </table>
                            </td>
                          </tr>

                          <tr>
                            <td style="border:1px solid #DDE8E2;background:#FFFFFF;border-radius:22px;overflow:hidden;box-shadow:0 26px 70px -46px rgba(31,76,65,0.42);">
                              <table role="presentation" width="100%%" cellspacing="0" cellpadding="0">
                                <tr>
                                  <td style="padding:30px 32px 22px 32px;background:linear-gradient(135deg,#F8FBFA 0%%,#FFFFFF 52%%,#EEF7F3 100%%);border-bottom:1px solid #E4E8E5;">
                                    <div style="display:inline-block;padding:6px 10px;border:1px solid #D7E8E0;border-radius:999px;background:#FFFFFF;color:#1F5F53;font-size:12px;font-weight:900;letter-spacing:0.08em;">SECURITY VERIFICATION</div>
                                    <h1 style="margin:18px 0 0 0;font-size:28px;line-height:1.14;font-weight:900;letter-spacing:-0.03em;color:#17211F;">杰创展示智能工作区验证码</h1>
                                    <p style="margin:12px 0 0 0;font-size:14px;line-height:1.7;color:#62706A;">您正在进行：<strong style="color:#17211F;">%s</strong>。请在当前页面输入下方验证码完成验证。</p>
                                  </td>
                                </tr>

                                <tr>
                                  <td style="padding:30px 32px;">
                                    <div style="font-size:12px;font-weight:900;color:#64726B;letter-spacing:0.14em;text-transform:uppercase;">Verification Code</div>
                                    <div style="margin-top:12px;padding:20px 22px;border:1px solid #D7E8E0;border-radius:16px;background:#F8FBFA;text-align:center;">
                                      <span style="font-family:'SFMono-Regular',Consolas,'Liberation Mono',monospace;font-size:42px;line-height:1;font-weight:900;letter-spacing:0.24em;color:#17211F;">%s</span>
                                    </div>
                                    <p style="margin:18px 0 0 0;font-size:13px;line-height:1.7;color:#64726B;">验证码 10 分钟内有效。为了账号安全，请勿将验证码转发给任何人，JC Display AI 工作人员也不会向您索要验证码。</p>

                                    <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="margin-top:24px;border-top:1px solid #E4E8E5;padding-top:18px;">
                                      <tr>
                                        <td style="font-size:13px;line-height:1.8;color:#52625A;">
                                          <strong style="color:#17211F;">公司名称：</strong>Shenzhen JC Display Packaging Co., Ltd.<br />
                                          <strong style="color:#17211F;">系统：</strong>JC Display AI Export Console<br />
                                          <strong style="color:#17211F;">权益声明：</strong>本邮件、品牌标识、系统界面与相关内容归杰创展示及其关联权利方所有，仅用于账号安全验证。
                                        </td>
                                      </tr>
                                    </table>
                                  </td>
                                </tr>
                              </table>
                            </td>
                          </tr>

                          <tr>
                            <td align="center" style="padding:18px 12px 0 12px;font-size:12px;line-height:1.7;color:#8A958F;">
                              如果不是您本人操作，请忽略本邮件。请勿回复此系统邮件。
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(leafLogo, action, code);
    }

    private String subjectFor(String purpose) {
        return switch (purpose) {
            case PURPOSE_LOGIN -> "JC Display AI 登录验证码";
            case PURPOSE_REGISTER -> "JC Display AI 注册验证码";
            case PURPOSE_RESET_PASSWORD -> "JC Display AI 找回密码验证码";
            case PURPOSE_BIND_QQ -> "JC Display AI QQ 邮箱绑定验证码";
            default -> "JC Display AI 验证码";
        };
    }

    private String actionFor(String purpose) {
        return switch (purpose) {
            case PURPOSE_LOGIN -> "登录工作区";
            case PURPOSE_REGISTER -> "创建新账号";
            case PURPOSE_RESET_PASSWORD -> "重置账号密码";
            case PURPOSE_BIND_QQ -> "绑定 QQ 邮箱";
            default -> "账号安全验证";
        };
    }
}
