package com.project___10.demo.controller;

import com.project___10.demo.dto.AdminUserDTO;
import com.project___10.demo.dto.UserStatusUpdateRequest;
import com.project___10.demo.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final SysUserService sysUserService;

    @GetMapping("/users")
    public List<AdminUserDTO> getAllUser() {
        return sysUserService.getAllUser();
    }

    @PostMapping("/users/status")
    public Map<String, Object> updateUserStatus(@RequestBody UserStatusUpdateRequest request) {
        sysUserService.updateUserStatus(
                request.getAdminUserId(),
                request.getUserId(),
                request.getStatus(),
                request.getReason()
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "success");
        result.put("userId", request.getUserId());
        result.put("status", request.getStatus());
        return result;
    }
}