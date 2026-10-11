package detect.vulexp.datastruct;

public class RiskyFuncInfo {
    public String accurate_class_name;
    public String accurate_method_name;
    public String not_accurate_class_name;
    public String not_accurate_method_name;
    public String string_value;
    public INFO_TYPE infoType;

    public enum INFO_TYPE {
        SOOT_METHOD_SIGNATURE, CLASS_AND_METHOD, CLASS_ALL_PUBLIC
    }

    public RiskyFuncInfo(String accurate_class_name, String accurate_method_name, String not_accurate_class_name, String not_accurate_method_name) {
        this.accurate_class_name = accurate_class_name;
        this.accurate_method_name = accurate_method_name;
        this.not_accurate_class_name = not_accurate_class_name;
        this.not_accurate_method_name = not_accurate_method_name;

        this.infoType = INFO_TYPE.CLASS_AND_METHOD;
    }

    public RiskyFuncInfo(String str, INFO_TYPE infoType){
        this.string_value = str;
        this.infoType = infoType;
    }

}
