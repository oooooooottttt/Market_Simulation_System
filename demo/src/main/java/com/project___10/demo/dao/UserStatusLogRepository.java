package com.project___10.demo.dao;

import com.project___10.demo.entity.UserStatusLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserStatusLogRepository extends JpaRepository<UserStatusLog, Long> {
}