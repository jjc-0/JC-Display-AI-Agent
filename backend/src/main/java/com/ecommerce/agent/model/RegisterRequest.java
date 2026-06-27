package com.ecommerce.agent.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度需要在 3-50 个字符之间")
    private String username;

    @NotBlank(message = "QQ 邮箱不能为空")
    private String qqEmail;

    @NotBlank(message = "验证码不能为空")
    private String code;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 100, message = "密码长度需要在 8-100 个字符之间")
    private String password;

    @NotBlank(message = "角色不能为空")
    @Pattern(regexp = "^(user|admin)$", message = "角色必须是 user 或 admin")
    private String role;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getQqEmail() { return qqEmail; }
    public void setQqEmail(String qqEmail) { this.qqEmail = qqEmail; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
