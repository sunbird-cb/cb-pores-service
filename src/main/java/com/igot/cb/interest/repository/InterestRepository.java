package com.igot.cb.interest.repository;

import com.igot.cb.demand.entity.DemandEntity;
import com.igot.cb.interest.entity.Interest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InterestRepository extends JpaRepository<Interest, String> {

}
