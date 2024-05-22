package com.igot.cb.interest.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.igot.cb.demand.entity.DemandEntity;
import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import java.sql.Timestamp;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "interest_capture")
@TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)
@Entity
public class Interest {

  @Id
  private String interestId;

  private String orgId;

  private String demandId;

  private String userId;

  @Type(type = "jsonb")
  @Column(columnDefinition = "jsonb")
  private JsonNode data;

  private boolean interestFlag;

  private Timestamp createdOn;

  private Timestamp updatedOn;

//  @ManyToOne
//  @JoinColumn(name = "demand_id") // Name of the foreign key column in interest_capture table
//  private DemandEntity demand;

}
