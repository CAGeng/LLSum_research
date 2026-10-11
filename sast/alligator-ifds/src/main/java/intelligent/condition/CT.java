package intelligent.condition;

import soot.SootMethod;

import java.util.HashMap;

public class CT {
    public int CT2;
    public boolean CT2_enable = false;

    public static HashMap<String, HashMap<SootMethod, Integer>> CT_relatedMethodParamIndex = new HashMap<>(); // 这里记录了当前CT的相关方法和参数位置 // 用于关联到所有相关的snapshot（可能会在调用栈的浅层冒险并snapshot）

    public boolean isEnabled() {
        if(CT2_enable) return true;
        return false;
    }

    public String getValidConditionString() {
        StringBuilder sb = new StringBuilder();
        if(CT2_enable) {
            sb.append("CT2-" + CT2);
        }
        return sb.toString();
    }

    public String getValidConditionStringDetailed() {
        StringBuilder sb = new StringBuilder();
        if(CT2_enable) {
            if(CT2 == 1){
                sb.append("(type boolean) is true");
            } else if (CT2 == 0) {
                sb.append("(type boolean) is false");
            }
        }
        return sb.toString();
    }

    public boolean satisfy(CT another) {
        // 看当前CT是否满足another的状态
        if(another.CT2_enable) {
            if(another.CT2 == 1 && CT2 == 0) {
                return false;
            }
            if(another.CT2 == 0 && CT2 == 1) {
                return false;
            }
        }
        return true;
    }

    // toString 函数
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if(CT2_enable) {
            sb.append("CT2-" + CT2);
        }
        return sb.toString();
    }
}
