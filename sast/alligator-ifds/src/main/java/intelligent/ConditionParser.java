package intelligent;

import intelligent.condition.CT;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConditionParser {

    public static class ParseResult {
        private int paramIndex;
        private String operator;
        private Object rightValue;

        public ParseResult(int paramIndex, String operator, Object rightValue) {
            this.paramIndex = paramIndex;
            this.operator = operator;
            this.rightValue = rightValue;
        }

        // Getters
        public int getParamIndex() { return paramIndex; }
        public String getOperator() { return operator; }
        public Object getRightValue() { return rightValue; }

        @Override
        public String toString() {
            return String.format("Param%d %s %s", paramIndex, operator, rightValue);
        }
    }

    private static final Pattern CONDITION_PATTERN = Pattern.compile(
            "\\s*(p\\d+)\\s*([!=<>]+)\\s*(true|false|null|\\d+|'.+?'|\".+?\")\\s*",
            Pattern.CASE_INSENSITIVE
    );

    public static ParseResult parseCondition(String condition) {
        Matcher matcher = CONDITION_PATTERN.matcher(condition);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid condition format: " + condition);
        }

        // 解析左边参数
        String left = matcher.group(1);
        int paramIndex = Integer.parseInt(left.replaceAll("\\D+", ""));

        // 解析操作符
        String operator = matcher.group(2).trim();

        // 解析右边值
        String right = matcher.group(3).trim();
        Object rightValue = parseRightValue(right);

        // 现在的条件中用p1表示第一个参数
        return new ParseResult(paramIndex - 1, operator, rightValue);
    }

    private static Object parseRightValue(String value) {
        if (value.equalsIgnoreCase("true")) return Boolean.TRUE;
        if (value.equalsIgnoreCase("false")) return Boolean.FALSE;
        if (value.equalsIgnoreCase("null")) return null;

        // 处理带引号的字符串
        if ((value.startsWith("'") && value.endsWith("'")) ||
                (value.startsWith("\"") && value.endsWith("\""))) {
            return value.substring(1, value.length()-1);
        }

        // 处理数字
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException e2) {
                return value; // 保底返回原始字符串
            }
        }
    }

    public static CT convertToCT(ParseResult result) {

        CT ct = new CT();
        String operator = result.getOperator();
        Object rightValue = result.getRightValue();

        // Convert value to boolean equivalent
        boolean isCT2 = true;
        boolean targetBoolean = false;
        if (rightValue instanceof Boolean) {
            targetBoolean = (Boolean) rightValue;
        } else if (rightValue instanceof Integer) {
            int val = (Integer) rightValue;
            if (val == 1) targetBoolean = true;
            else if (val == 0) targetBoolean = false;
            else isCT2 = false;
        } else if (rightValue instanceof String) {
            String s = ((String) rightValue).toLowerCase();
            if (s.equals("true")) targetBoolean = true;
            else if (s.equals("false")) targetBoolean = false;
            else isCT2 = false;
        } else {
            isCT2 = false;
        }

        // Handle boolean logic
        if(isCT2) {
            if (operator.equals("==")) {
                ct.CT2 = targetBoolean ? 1 : 0;
                ct.CT2_enable = true;
            } else if (operator.equals("!=")) {
                ct.CT2 = targetBoolean ? 0 : 1;
                ct.CT2_enable = true;
            }
        } else {
            throw new IllegalArgumentException("Only supports == and != operators");
        }

        return ct;
    }

    public static void main(String[] args) {
        String[] testCases = {
                "p1 == true",
                "p2 != false",
                "p3 >= 5",
                "p4 <= null",
                "p5 == 'hello'",
                "p6 != \"world\""
        };

        for (String test : testCases) {
            try {
                ParseResult result = parseCondition(test);
                System.out.println(test + " => " + result);
            } catch (Exception e) {
                System.err.println("Error parsing: " + test + " - " + e.getMessage());
            }
        }
    }
}