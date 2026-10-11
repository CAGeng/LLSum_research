package org.foo.service;

import app.importer.entity.SourceRule;
import org.foo.model.AnalysisRequest;
import org.foo.model.db.DBSourceRule;

public interface RuleService {

    boolean addSourceRule(SourceRule sourceRule);

}
