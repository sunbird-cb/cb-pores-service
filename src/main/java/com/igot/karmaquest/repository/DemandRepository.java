package com.igot.karmaquest.repository;

import com.igot.karmaquest.entity.DemandEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DemandRepository extends JpaRepository<DemandEntity, String> {

}
