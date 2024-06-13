package com.igot.cb.cios.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ObjectDto implements Serializable {
    private String identifier;
    private String competencyArea;
}
