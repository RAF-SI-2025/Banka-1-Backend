package com.banka1.order.repository;

import com.banka1.order.entity.ActuaryProfitRollup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActuaryProfitRollupRepository extends JpaRepository<ActuaryProfitRollup, Long> {

    List<ActuaryProfitRollup> findAllByOrderByEmployeeIdAsc();
}
