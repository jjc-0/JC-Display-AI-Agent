package com.ecommerce.agent.controller;

import com.ecommerce.agent.config.JwtUtil;
import com.ecommerce.agent.model.AuthResponse;
import com.ecommerce.agent.model.LoginRequest;
import com.ecommerce.agent.model.RegisterRequest;
import com.ecommerce.agent.model.User;
import com.ecommerce.agent.repository.UserRepository;
import com.ecommerce.agent.service.EmailVerificationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final EmailVerificationService emailVerificationService;

    public AuthController(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          JwtUtil jwtUtil,
                          EmailVerificationService emailVerificationService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.emailVerificationService = emailVerificationService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        String username = clean(request.getUsername(), 50);
        String qqEmail = emailVerificationService.normalizeEmail(request.getQqEmail());

        if (!emailVerificationService.isQqEmail(qqEmail)) {
            return ResponseEntity.badRequest().body(Map.of("message", "请输入有效的 QQ 邮箱"));
        }
        if (userRepository.existsByUsername(username)) {
            return ResponseEntity.badRequest().body(Map.of("message", "该用户名已被注册"));
        }
        if (userRepository.findByQqEmail(qqEmail).isPresent() || userRepository.findByEmail(qqEmail).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("message", "该 QQ 邮箱已绑定其他账号"));
        }
        if (!emailVerificationService.verify(qqEmail, EmailVerificationService.PURPOSE_REGISTER, request.getCode())) {
            return ResponseEntity.status(400).body(Map.of("message", "验证码错误或已过期"));
        }

        User user = new User(username, passwordEncoder.encode(request.getPassword()), request.getRole());
        user.setQqEmail(qqEmail);
        user.setEmail(qqEmail);
        userRepository.save(user);

        return ResponseEntity.ok(authResponse(user));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        String account = clean(request.getAccount(), 120);
        Optional<User> optUser = userRepository.findByUsername(account);
        if (optUser.isEmpty()) optUser = userRepository.findByEmail(emailVerificationService.normalizeEmail(account));
        if (optUser.isEmpty()) optUser = userRepository.findByQqEmail(emailVerificationService.normalizeEmail(account));
        if (optUser.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("message", "账号或密码错误"));
        }

        User user = optUser.get();
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return ResponseEntity.status(401).body(Map.of("message", "账号或密码错误"));
        }

        return ResponseEntity.ok(authResponse(user));
    }

    @PostMapping("/login/code")
    public ResponseEntity<?> loginByCode(@RequestBody Map<String, Object> body) {
        String email = emailVerificationService.normalizeEmail(clean(body.get("email"), 120));
        String code = clean(body.get("code"), 20);
        if (!emailVerificationService.verify(email, EmailVerificationService.PURPOSE_LOGIN, code)) {
            return ResponseEntity.status(401).body(Map.of("message", "验证码错误或已过期"));
        }

        Optional<User> optUser = userRepository.findByQqEmail(email);
        if (optUser.isEmpty()) optUser = userRepository.findByEmail(email);
        if (optUser.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("message", "该邮箱尚未绑定账号"));
        }

        return ResponseEntity.ok(authResponse(optUser.get()));
    }

    @PostMapping("/code/send")
    public ResponseEntity<?> sendCode(@RequestBody Map<String, Object> body) {
        String email = emailVerificationService.normalizeEmail(clean(body.get("email"), 120));
        String purpose = clean(body.get("purpose"), 30);
        if (!emailVerificationService.isQqEmail(email)) {
            return ResponseEntity.badRequest().body(Map.of("message", "请输入有效的 QQ 邮箱"));
        }
        if (!isAllowedPurpose(purpose)) {
            return ResponseEntity.badRequest().body(Map.of("message", "验证码用途无效"));
        }
        if (EmailVerificationService.PURPOSE_REGISTER.equals(purpose)) {
            if (userRepository.findByQqEmail(email).isPresent() || userRepository.findByEmail(email).isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("message", "该 QQ 邮箱已绑定账号"));
            }
        }
        if (EmailVerificationService.PURPOSE_LOGIN.equals(purpose)
                || EmailVerificationService.PURPOSE_RESET_PASSWORD.equals(purpose)) {
            boolean exists = userRepository.findByQqEmail(email).isPresent() || userRepository.findByEmail(email).isPresent();
            if (!exists) {
                return ResponseEntity.badRequest().body(Map.of("message", "该邮箱尚未绑定账号"));
            }
        }

        try {
            emailVerificationService.sendCode(email, purpose);
            return ResponseEntity.ok(Map.of("success", true, "message", "验证码已发送"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/password/reset")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, Object> body) {
        String email = emailVerificationService.normalizeEmail(clean(body.get("email"), 120));
        String code = clean(body.get("code"), 20);
        String newPassword = clean(body.get("newPassword"), 120);
        String confirmPassword = clean(body.get("confirmPassword"), 120);
        if (newPassword.length() < 8) {
            return ResponseEntity.badRequest().body(Map.of("message", "新密码至少需要 8 个字符"));
        }
        if (!newPassword.equals(confirmPassword)) {
            return ResponseEntity.badRequest().body(Map.of("message", "两次输入的新密码不一致"));
        }
        if (!emailVerificationService.verify(email, EmailVerificationService.PURPOSE_RESET_PASSWORD, code)) {
            return ResponseEntity.status(400).body(Map.of("message", "验证码错误或已过期"));
        }

        Optional<User> optUser = userRepository.findByQqEmail(email);
        if (optUser.isEmpty()) optUser = userRepository.findByEmail(email);
        if (optUser.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "该邮箱尚未绑定账号"));
        }
        User user = optUser.get();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("success", true, "message", "密码已重置，请重新登录"));
    }

    @GetMapping("/username/check")
    public ResponseEntity<?> checkUsername(@RequestParam String username,
                                           @RequestHeader(value = "Authorization", required = false) String authorization) {
        String cleanUsername = clean(username, 50);
        if (cleanUsername.length() < 3) {
            return ResponseEntity.badRequest().body(Map.of("available", false, "message", "用户名至少需要 3 个字符"));
        }
        Optional<User> current = currentUser(authorization);
        boolean exists = current
                .map(user -> userRepository.existsByUsernameAndIdNot(cleanUsername, user.getId()))
                .orElseGet(() -> userRepository.existsByUsername(cleanUsername));
        return ResponseEntity.ok(Map.of("available", !exists));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        Optional<User> current = currentUser(authorization);
        if (current.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("message", "登录已失效，请重新登录"));
        }
        return ResponseEntity.ok(toProfile(current.get()));
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestHeader(value = "Authorization", required = false) String authorization,
                                           @RequestBody Map<String, Object> body) {
        Optional<User> current = currentUser(authorization);
        if (current.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("message", "登录已失效，请重新登录"));
        }

        User user = current.get();
        boolean usernameChanged = false;
        String username = clean(body.get("username"), 50);
        if (!username.isBlank() && !username.equals(user.getUsername())) {
            if (username.length() < 3) {
                return ResponseEntity.badRequest().body(Map.of("message", "用户名至少需要 3 个字符"));
            }
            if (userRepository.existsByUsernameAndIdNot(username, user.getId())) {
                return ResponseEntity.badRequest().body(Map.of("message", "该用户名已被使用"));
            }
            user.setUsername(username);
            usernameChanged = true;
        }
        user.setDisplayName(clean(body.get("displayName"), 80));
        user.setEmail(clean(body.get("email"), 120));
        user.setCompanyName(clean(body.get("companyName"), 120));
        user.setDepartment(clean(body.get("department"), 80));
        user.setJobTitle(clean(body.get("jobTitle"), 80));
        user.setPhone(clean(body.get("phone"), 40));
        userRepository.save(user);

        Map<String, Object> profile = toProfile(user);
        if (usernameChanged) {
            profile.put("token", jwtUtil.generateToken(user.getUsername(), user.getRole()));
        }
        return ResponseEntity.ok(profile);
    }

    @PostMapping("/qq-email/bind")
    public ResponseEntity<?> bindQqEmail(@RequestHeader(value = "Authorization", required = false) String authorization,
                                         @RequestBody Map<String, Object> body) {
        Optional<User> current = currentUser(authorization);
        if (current.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("message", "登录已失效，请重新登录"));
        }
        User user = current.get();
        String qqEmail = emailVerificationService.normalizeEmail(clean(body.get("qqEmail"), 120));
        String code = clean(body.get("code"), 20);
        if (!emailVerificationService.isQqEmail(qqEmail)) {
            return ResponseEntity.badRequest().body(Map.of("message", "请输入有效的 QQ 邮箱"));
        }
        if (userRepository.existsByQqEmailAndIdNot(qqEmail, user.getId())) {
            return ResponseEntity.badRequest().body(Map.of("message", "该 QQ 邮箱已绑定其他账号"));
        }
        if (!emailVerificationService.verify(qqEmail, EmailVerificationService.PURPOSE_BIND_QQ, code)) {
            return ResponseEntity.status(400).body(Map.of("message", "验证码错误或已过期"));
        }
        user.setQqEmail(qqEmail);
        user.setEmail(qqEmail);
        userRepository.save(user);
        return ResponseEntity.ok(toProfile(user));
    }

    @PostMapping("/avatar")
    public ResponseEntity<?> uploadAvatar(@RequestHeader(value = "Authorization", required = false) String authorization,
                                          @RequestParam("file") MultipartFile file) throws IOException {
        Optional<User> current = currentUser(authorization);
        if (current.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("message", "登录已失效，请重新登录"));
        }
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "请选择头像图片"));
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().body(Map.of("message", "只能上传图片文件"));
        }
        if (file.getSize() > 2 * 1024 * 1024) {
            return ResponseEntity.badRequest().body(Map.of("message", "头像图片不能超过 2MB"));
        }

        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = original.contains(".") ? original.substring(original.lastIndexOf(".")).toLowerCase(Locale.ROOT) : ".png";
        if (!ext.matches("\\.(png|jpg|jpeg|webp|gif)$")) ext = ".png";

        Path dir = Paths.get("uploads", "avatars").toAbsolutePath().normalize();
        Files.createDirectories(dir);
        String filename = "avatar-" + current.get().getId() + "-" + UUID.randomUUID() + ext;
        Path target = dir.resolve(filename).normalize();
        Files.copy(file.getInputStream(), target);

        User user = current.get();
        user.setAvatarUrl("/uploads/avatars/" + filename);
        userRepository.save(user);
        return ResponseEntity.ok(toProfile(user));
    }

    @PutMapping("/password")
    public ResponseEntity<?> updatePassword(@RequestHeader(value = "Authorization", required = false) String authorization,
                                            @RequestBody Map<String, Object> body) {
        Optional<User> current = currentUser(authorization);
        if (current.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("message", "登录已失效，请重新登录"));
        }

        String currentPassword = clean(body.get("currentPassword"), 120);
        String newPassword = clean(body.get("newPassword"), 120);
        String confirmPassword = clean(body.get("confirmPassword"), 120);
        if (newPassword.length() < 8) {
            return ResponseEntity.badRequest().body(Map.of("message", "新密码至少需要 8 个字符"));
        }
        if (!newPassword.equals(confirmPassword)) {
            return ResponseEntity.badRequest().body(Map.of("message", "两次输入的新密码不一致"));
        }

        User user = current.get();
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            return ResponseEntity.status(400).body(Map.of("message", "当前密码不正确"));
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("success", true, "message", "密码已更新"));
    }

    private AuthResponse authResponse(User user) {
        return new AuthResponse(jwtUtil.generateToken(user.getUsername(), user.getRole()), user.getUsername(), user.getRole());
    }

    private Optional<User> currentUser(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return Optional.empty();
        }
        String token = authorization.substring("Bearer ".length()).trim();
        if (!jwtUtil.validateToken(token)) {
            return Optional.empty();
        }
        return userRepository.findByUsername(jwtUtil.getUsernameFromToken(token));
    }

    private Map<String, Object> toProfile(User user) {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", user.getId());
        profile.put("username", user.getUsername());
        profile.put("role", user.getRole());
        profile.put("displayName", valueOrDefault(user.getDisplayName(), user.getUsername()));
        profile.put("email", valueOrDefault(user.getEmail(), ""));
        profile.put("qqEmail", valueOrDefault(user.getQqEmail(), ""));
        profile.put("avatarUrl", valueOrDefault(user.getAvatarUrl(), ""));
        profile.put("companyName", valueOrDefault(user.getCompanyName(), ""));
        profile.put("department", valueOrDefault(user.getDepartment(), ""));
        profile.put("jobTitle", valueOrDefault(user.getJobTitle(), ""));
        profile.put("phone", valueOrDefault(user.getPhone(), ""));
        profile.put("createdAt", user.getCreatedAt() != null ? user.getCreatedAt().toString() : null);
        profile.put("updatedAt", user.getUpdatedAt() != null ? user.getUpdatedAt().toString() : null);
        return profile;
    }

    private String clean(Object value, int maxLength) {
        if (value == null) return "";
        String s = value.toString().trim();
        if (s.length() > maxLength) return s.substring(0, maxLength);
        return s;
    }

    private boolean isAllowedPurpose(String purpose) {
        return EmailVerificationService.PURPOSE_LOGIN.equals(purpose)
                || EmailVerificationService.PURPOSE_REGISTER.equals(purpose)
                || EmailVerificationService.PURPOSE_RESET_PASSWORD.equals(purpose)
                || EmailVerificationService.PURPOSE_BIND_QQ.equals(purpose);
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
