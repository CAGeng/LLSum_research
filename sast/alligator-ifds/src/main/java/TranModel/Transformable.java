package TranModel;

import detect.dataflow.DataFlowTrack;
import detect.knowledge.constraint.ConstraintGen;

import java.util.ArrayList;
import java.util.List;

public class Transformable {
    public enum State{
        ORIGIN, TRANSFORMED, TERMINATED
    }

    static List<Rule> rules = new ArrayList<>();

    static List<InfoRule> infoRules = new ArrayList<>();

    static List<ConstraintGen> constraintGens = new ArrayList<>();

    public State state = State.ORIGIN;

    public static void clearRules(){
        rules = new ArrayList<>();
    }
    public static void clearInfoRules(){
        infoRules = new ArrayList<>();
    }

    public static void clearConstraintGen(){
        constraintGens = new ArrayList<>();
    }

    public static void addRule(Rule rule){
        rules.add(rule);
    }

    public static void addInfoRule(InfoRule infoRule){
        infoRules.add(infoRule);
    }

    public static void addConstraintGen(ConstraintGen constraintGen){
        constraintGens.add(constraintGen);
    }

    public static Rule getRule(Class ruleClass){
        for(Rule rule : rules){
            if(ruleClass.equals(rule.getClass())) return rule;
        }
        return null;
    }

    public void forward(){
        state = State.TRANSFORMED;
        for(Rule rule : rules){
            rule.apply(this);
        }
    }

    public void forward(DataFlowTrack.DataFlowResult dataFlowResult){
        state = State.TRANSFORMED;
        for(InfoRule infoRule : infoRules){
            infoRule.apply(this, dataFlowResult);
        }
    }

    public void constraintsGen(DataFlowTrack.DataFlowResult dataFlowResult){
        for (ConstraintGen constraintGen: constraintGens){
            constraintGen.recordConstraint(dataFlowResult, this);
        }
    }

    public void forwardAfterRecurse(){
        for(Rule rule : rules){
            rule.applyAfterRecurse(this);
        }
    }

    public void terminate(){
        state = State.TERMINATED;
    }

    public boolean isTerminated(){
        return state.equals(State.TERMINATED);
    }
}
