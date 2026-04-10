package com.project___10.demo.dao;

import com.project___10.demo.entity.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PortfolioRepository extends JpaRepository<Portfolio,Long> {
    List<Portfolio> findByUserId(Long userId);

    Portfolio findByUserIdAndTicker(Long userId, String ticker);
}
