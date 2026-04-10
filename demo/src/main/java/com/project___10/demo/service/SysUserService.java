package com.project___10.demo.service;
import com.project___10.demo.common.Result;
import com.project___10.demo.dto.AdminUserDTO;
import com.project___10.demo.entity.SysUser;

import java.math.BigDecimal;
import java.util.List;

public interface SysUserService {

    SysUser getUserById(long id);

    SysUser login(String username, String password);

    // 扣减余额
    void deductBalance(long id, BigDecimal amount);
    // 增加余额
    void addBalance(long id, BigDecimal amount);

    List<AdminUserDTO> getAllUser();

    void updateUserStatus(Long adminUserId, Long userId, Integer status, String reason);

    Result register(SysUser user);
}
