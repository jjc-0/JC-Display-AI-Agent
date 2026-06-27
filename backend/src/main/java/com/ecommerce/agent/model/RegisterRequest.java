package com.ecommerce.agent.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegisterRequest {

    @NotBlank(message = "账号不能为空")
    @Size(min = 3, max = 50, message = "账号长度需在3-50个字符之间")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 100, message = "密码长度需在6-100个字符之间")
    private String password;

    @NotBlank(message = "角色不能为空")
    @Pattern(regexp = "^(user|admin)$", message = "角色必须是 user 或 admin")
    private String role;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
