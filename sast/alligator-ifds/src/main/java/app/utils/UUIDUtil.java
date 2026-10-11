package app.utils;

import java.util.Random;
import java.util.UUID;

public class UUIDUtil {

    public static String generateRuleID() {
        Random random = new Random();
        String hexValue = Long.toHexString(random.nextLong());
        while (hexValue.length() < 32) {
            hexValue = "0" + hexValue;
        }
        String RuleId = String.format(
                "%s-%s-%s-%s-%s",
                hexValue.substring(0, 8),
                hexValue.substring(8, 12),
                hexValue.substring(12, 16),
                hexValue.substring(16, 20),
                hexValue.substring(20, 32)
        );
        return RuleId;
    }

    public static String generateRandomNumber(int size) {
        Random random = new Random();
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < size; i++) {
            int digit = random.nextInt(size);
            stringBuilder.append(digit);
        }
        return stringBuilder.toString();
    }
}
