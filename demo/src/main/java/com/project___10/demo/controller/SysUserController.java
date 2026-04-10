package com.project___10.demo.controller;

import com.project___10.demo.common.Result;
import com.project___10.demo.entity.SysUser;
import com.project___10.demo.service.SysUserService;
import com.project___10.demo.vo.UserVo;
import lombok.RequiredArgsConstructor; // 【新增】引入 Lombok 注解
import org.apache.catalina.User;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor // Lombok 会自动为所有带 final 的字段生成构造函数！
public class SysUserController {

    // 不建议使用注入就删掉 @Autowired，加上 final，，但是需要无参构造，就利用@RequiredArgsConstructor
    private final SysUserService sysUserService;

    @GetMapping("/users/{id}")
    public UserVo getUserById(@PathVariable("id") Long id) {
        UserVo userVo = new UserVo();
        SysUser user = sysUserService.getUserById(id);
//        userVo.setUsername(user.getUsername());
//        userVo.setId(user.getId());
//        userVo.setBalance(user.getBalance());
        // 把user里的同名属性全拷进 userVo 里，不用手写全部set了
        org.springframework.beans.BeanUtils.copyProperties(user, userVo);
        return userVo;
    }

    @PostMapping("/login")
    public UserVo login(@RequestParam("username") String username, @RequestParam("password") String password) {
        // 1. 执行登录，拿到完整的用户信息
        SysUser user = sysUserService.login(username, password);

        // 2. new Vo对象
        UserVo userVo= new UserVo();
        org.springframework.beans.BeanUtils.copyProperties(user, userVo);

        // 3. 返回VO对象
        return userVo;
    }

    //使用 POST 接收前端发来的 JSON 数据
    @PostMapping("/register")
    public Result register(@RequestBody SysUser user) {
        // 基本的非空校验
        if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            return Result.error("用户名不能为空");
        }
        if (user.getPassword() == null || user.getPassword().length() < 6) {
            return Result.error("密码不能少于 6 位");
        }

        return sysUserService.register(user);
    }
}