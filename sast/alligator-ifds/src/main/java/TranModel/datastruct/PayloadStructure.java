package TranModel.datastruct;

import basic.util.Util;
import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class PayloadStructure {

    public int entryParamInd;

    public List<String> accessPaths = new LinkedList<>(); // class -> field -> field
    public String baseAccessPath;
    public HashMap<String, String> schemas = new HashMap<>(); // accessPath map to type

    public PayloadStructure(){}

    public PayloadStructure(SootClass sootClass, int entryParamInd) {
        this.entryParamInd = entryParamInd;
        findAccessPaths(sootClass, new ArrayList<>());
        baseAccessPath = sootClass.getName();
    }

    public void findAccessPaths(SootClass sootClass, List<String> currentPath) {
        currentPath.add(sootClass.getName());
        if(currentPath.size() > 3) return;
        // 遍历类的字段
        for (SootField field : sootClass.getFields()) {
            String fieldName = field.getName();
            currentPath.add(fieldName);

            Type fieldType = field.getType();
            if (!Util.isPrimitive(fieldType)) {
                SootClass fieldClass = Scene.v().getSootClassUnsafe(fieldType.toString());
                if(fieldClass == null){
                    continue;
                }
                findAccessPaths(fieldClass, new ArrayList<>(currentPath)); // 递归处理字段的字段
            } else {
                accessPaths.add(String.join(" -> ", currentPath));
                schemas.put(String.join(" -> ", currentPath), fieldType.toString());
            }

            currentPath.remove(currentPath.size() - 1); // 移除最后一个字段，以便添加下一个字段
        }
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        for (String accessPath : accessPaths) {
            stringBuilder.append(accessPath);
            stringBuilder.append("\n");
        }
        return stringBuilder.toString();
    }
}
