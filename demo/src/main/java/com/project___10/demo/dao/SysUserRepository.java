package com.project___10.demo.dao;

import com.project___10.demo.entity.SysUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SysUserRepository extends JpaRepository<SysUser,Long> {
    Optional<SysUser> findByUsername(String username);

    List<SysUser> findAllByOrderByCreatedAtDesc();

    @Modifying
    @Transactional
    @Query("update SysUser u set u.status = :status, u.updatedAt = :updatedAt where u.id = :userId")
    int updateStatus(@Param("userId") Long userId,
                     @Param("status") Integer status,
                     @Param("updatedAt") LocalDateTime updatedAt);
}
