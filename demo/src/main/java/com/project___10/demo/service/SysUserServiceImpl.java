package com.project___10.demo.service;

import com.project___10.demo.common.Result;
import com.project___10.demo.dao.SysUserRepository;
import com.project___10.demo.dao.UserStatusLogRepository;
import com.project___10.demo.dto.AdminUserDTO;
import com.project___10.demo.entity.SysUser;
import com.project___10.demo.entity.UserStatusLog;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime; // 🌟 导入时间类
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysUserServiceImpl implements SysUserService {

    private final SysUserRepository sysUserRepository;
    private final UserStatusLogRepository userStatusLogRepository;

    @Override
    public SysUser getUserById(long id) {
        return sysUserRepository.findById(id).orElseThrow(RuntimeException::new);
    }

    @Override
    public SysUser login(String username, String password) {
        //1. 先去数据库里找这个用户名
        System.out.println("=== 登录校验开始 ===");
        SysUser user = sysUserRepository.findByUsername(username).orElseThrow(() -> new RuntimeException("用户名不存在！"));

        System.out.println("前端传来的密码: [" + password + "]");
        System.out.println("数据库里的密码: [" + user.getPassword() + "]");
        System.out.println("====================");

        //2. 核对密码 (企业级开发以后这里会用 BCrypt 密文比对，这里先用明文)
        if (!user.getPassword().equals(password)) {
            throw new RuntimeException("密码错误！");
        }

        //3. 校验通过，把用户信息返回给前端
        return user;
    }

    @Override
    @Transactional // 保证单一操作的原子性
    public void deductBalance(long id, BigDecimal amount) {
        SysUser user = sysUserRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        //核心校验：用 compareTo 判断余额是否足够
        //compareTo 返回 -1 表示小于，0 表示等于，1 表示大于
        if (user.getBalance().compareTo(amount) < 0) {
            //余额不够，直接掀桌子抛异常！这会让最外层的事务瞬间回滚！
            throw new RuntimeException("余额不足，当前余额: $" + user.getBalance());
        }

        //扣钱、更新时间并保存
        user.setBalance(user.getBalance().subtract(amount));
        user.setUpdatedAt(LocalDateTime.now()); //加上更新时间
        sysUserRepository.save(user);
    }

    @Override
    @Transactional
    public void addBalance(long id, BigDecimal amount) {
        SysUser user = sysUserRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        //加钱、更新时间并保存
        user.setBalance(user.getBalance().add(amount));
        user.setUpdatedAt(LocalDateTime.now()); //加上更新时间
        sysUserRepository.save(user);
    }


    @Override
    @Transactional
    public void updateUserStatus(Long adminUserId, Long userId, Integer status, String reason) {
        if (adminUserId == null) {
            throw new RuntimeException("adminUserId is required");
        }
        if (userId == null || status == null) {
            throw new RuntimeException("userId and status are required");
        }
        if (!SysUser.STATUS_NORMAL.equals(status) && !SysUser.STATUS_FROZEN.equals(status)) {
            throw new RuntimeException("Invalid status value");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new RuntimeException("Reason is required");
        }

        SysUser adminUser = sysUserRepository.findById(adminUserId)
                .orElseThrow(() -> new RuntimeException("Admin user not found"));
        if (!SysUser.ROLE_ADMIN.equalsIgnoreCase(adminUser.getRole())) {
            throw new RuntimeException("No admin permission");
        }

        SysUser targetUser = sysUserRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Target user not found"));
        if (SysUser.ROLE_ADMIN.equalsIgnoreCase(targetUser.getRole())) {
            throw new RuntimeException("Admin account cannot be updated here");
        }

        int updatedRows = sysUserRepository.updateStatus(userId, status, LocalDateTime.now());
        if (updatedRows < 1) {
            throw new RuntimeException("Update user status failed");
        }

        UserStatusLog log = new UserStatusLog();
        log.setAdminUserId(adminUserId);
        log.setTargetUserId(userId);
        log.setOperationType(SysUser.STATUS_FROZEN.equals(status) ? 1 : 2);
        log.setTargetStatus(status);
        log.setReason(reason.trim());
        log.setCreatedAt(LocalDateTime.now());
        userStatusLogRepository.save(log);
    }

    @Override
    public List<AdminUserDTO> getAllUser() {
        return sysUserRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::convertToAdminUserDto)
                .collect(Collectors.toList());
    }

    private AdminUserDTO convertToAdminUserDto(SysUser user) {
        AdminUserDTO dto = new AdminUserDTO();
        BeanUtils.copyProperties(user, dto);
        return dto;
    }

    @Override
    @Transactional
    public Result register(SysUser user) {
        //核心校验：查重！绝不能让两个同名的人注册
        if (sysUserRepository.findByUsername(user.getUsername()).isPresent()) {
            return Result.error("用户名已存在，请换一个试试！");
        }

        SysUser newUser = new SysUser();
        newUser.setUsername(user.getUsername());
        newUser.setPassword(user.getPassword()); // 真实的金融系统这里必须加密，我们暂且存明文

        newUser.setBalance(new BigDecimal("100000.00"));

        newUser.setStatus(1);
        newUser.setRole("USER");

        newUser.setCreatedAt(LocalDateTime.now());
        newUser.setUpdatedAt(LocalDateTime.now());

        sysUserRepository.save(newUser);

        return Result.success("注册成功！快去登录吧！");
    }


}