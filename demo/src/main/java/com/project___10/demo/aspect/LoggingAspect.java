package com.project___10.demo.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Aspect
@Component
@Slf4j       //Lombok 提供的日志对象，直接用 log.info() 即可
public class LoggingAspect {

    // 定义监控范围 (Pointcut 切入点)，拦截 com.project___10.demo.service 包下，所有的类、所有的方法
    @Pointcut("execution(* com.project___10.demo.service.*.*(..))")
    public void servicePointcut() {
    }

    //核心逻辑 (Around 环绕通知)，在方法执行前后打印输出日志
    @Around("servicePointcut()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        // 抓取当前正在被调用的类名和方法名
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        // 抓取前端传进来的参数
        String args = Arrays.toString(joinPoint.getArgs());

        log.info("▶️ [AOP 监控] 进入服务: {}.{}() | 携带参数: {}", className, methodName, args);

        long startTime = System.currentTimeMillis();

        try {
            //这一句是整个 AOP 的灵魂！意思是让原有的 Service 方法去执行！
            Object result = joinPoint.proceed();
            // 【探头记录 - 炒菜后】
            long elapsedTime = System.currentTimeMillis() - startTime;
            log.info("✅ [AOP 监控] 离开服务: {}.{}() | 耗时: {}ms | 吐出结果: {}",
                    className, methodName, elapsedTime, result);
            return result; // 把结果原封不动地交还给 Controller

        } catch (IllegalArgumentException e) {
            log.error("❌ [AOP 监控] 服务异常: {}.{}() | 错误信息: {}", className, methodName, e.getMessage());
            throw e;
        }
    }
}