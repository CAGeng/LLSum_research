package detect.vulexp;

import detect.vulexp.datastruct.RiskyFuncInfo;
import lombok.extern.slf4j.Slf4j;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.util.Chain;

import java.util.LinkedList;
import java.util.List;

@Slf4j
public class SearchFuncUtil {
    public static List<SootMethod> searchRiskyFuncs(RiskyFuncInfo riskyFuncInfo){
        List<SootMethod> targets = new LinkedList<>();

        if(riskyFuncInfo.infoType == RiskyFuncInfo.INFO_TYPE.SOOT_METHOD_SIGNATURE) {
            try {
                SootMethod method = Scene.v().getMethod(riskyFuncInfo.string_value);
                targets.add(method);
//                log.warn("add sink: " + method);
            } catch (Exception ignored){
//                ignored.printStackTrace();
            }
            return targets;
        } else if(riskyFuncInfo.infoType == RiskyFuncInfo.INFO_TYPE.CLASS_ALL_PUBLIC) {
            Chain<SootClass> clzs = Scene.v().getApplicationClasses();
            for(SootClass sootClass : clzs) {
                if(sootClass.getName().equals(riskyFuncInfo.string_value)) {
                    for(SootMethod sootMethod : sootClass.getMethods()) {
                        if(sootMethod.isPublic()) {
                            targets.add(sootMethod);
                        }
                    }
                }
            }
            return targets;
        }

        if(riskyFuncInfo.infoType == RiskyFuncInfo.INFO_TYPE.CLASS_AND_METHOD) {
            Chain<SootClass> clzs = Scene.v().getApplicationClasses();

            // 精准定位方式，class和method名字都是准确字符串
            if(riskyFuncInfo.accurate_method_name != null && riskyFuncInfo.accurate_class_name != null){
                for(SootClass sootClass : clzs) {
                    if (sootClass.getName().equals(riskyFuncInfo.accurate_class_name)) {
                        for(SootMethod sootMethod : sootClass.getMethods()) {
                            if(sootMethod.getName().equals(riskyFuncInfo.accurate_method_name)) {
                                targets.add(sootMethod);
                            }
                        }
                    }
                }
                return targets;
            }

            // 模糊的方式匹配触发点的sootmethod
            // todo
            SootClass vulnerableClass = null;
            for(SootClass sootClass : clzs) {
                if(riskyFuncInfo.not_accurate_class_name != null) {
                    if (sootClass.getName().toLowerCase().endsWith("." + riskyFuncInfo.not_accurate_class_name.toLowerCase())) {
                        vulnerableClass = sootClass;
                        System.out.print("find class" + vulnerableClass + "\n");
                    }
                }
                if(riskyFuncInfo.accurate_class_name != null) {
                    if (sootClass.getName().equals(riskyFuncInfo.accurate_class_name)){
                        vulnerableClass = sootClass;
                    }
                }
            }
            if(vulnerableClass != null) {
                for(SootMethod sootMethod : vulnerableClass.getMethods()) {
                    if(riskyFuncInfo.accurate_method_name != null) {
                        if(sootMethod.getName().equals(riskyFuncInfo.accurate_method_name)) {
                            targets.add(sootMethod);
                        }
                    }
                    if(riskyFuncInfo.not_accurate_method_name != null) {
                        if(sootMethod.getName().toLowerCase().contains(riskyFuncInfo.not_accurate_method_name.toLowerCase())) {
                            targets.add(sootMethod);
                        }
                    }
                }
            }

            return targets;
        }

        return targets;
    }

    public static List<SootMethod> searchMethods(List<String> pkgNameList, boolean isPublic) {
        List<SootMethod> methods = new LinkedList<>();
        for(SootClass sootClass : Scene.v().getApplicationClasses()) {
            boolean inDownStream = false;
            for(String pkgName : pkgNameList) {
                if(sootClass.getName().contains(pkgName)) {
                    inDownStream = true;
                    break;
                }
            }
            if(inDownStream) {
                for(SootMethod sootMethod : sootClass.getMethods()) {
                    if(!isPublic || sootMethod.isPublic()){
                        methods.add(sootMethod);
                    }
                }
            }
        }
        return methods;
    }
}
