package org.foo.model.db;


import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class TriggerPoint {

    private String ossId;

    private String cveId;

    private String project;

    private String classes;

    private String methods;

    private String isComplete;




}
