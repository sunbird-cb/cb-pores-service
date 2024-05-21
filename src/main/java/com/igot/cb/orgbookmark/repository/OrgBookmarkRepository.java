package com.igot.cb.orgbookmark.repository;

import com.igot.cb.orgbookmark.entity.OrgBookmarkEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrgBookmarkRepository extends JpaRepository<OrgBookmarkEntity, String> {
}

