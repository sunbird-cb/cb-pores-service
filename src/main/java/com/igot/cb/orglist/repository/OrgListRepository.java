package com.igot.cb.orglist.repository;

import com.igot.cb.orglist.entity.OrgListEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrgListRepository extends JpaRepository<OrgListEntity, String> {
}

