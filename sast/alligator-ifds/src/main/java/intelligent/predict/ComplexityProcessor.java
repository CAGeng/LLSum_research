package intelligent.predict;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ComplexityProcessor {

    enum ComplexityRange {
        ZERO, ONE_TO_TWENTY, TWENTY_ONE_TO_HUNDRED, HUNDRED_ONE_TO_FIVE_HUNDRED,
        FIVE_HUNDRED_ONE_TO_TWO_THOUSAND, TWO_THOUSAND_ONE_TO_FIVE_THOUSAND,
        FIVE_THOUSAND_ONE_TO_TWENTY_THOUSAND, TWENTY_THOUSAND_TO_HUNDRED_THOUSAND,
        HUNDRED_THOUSAND_TO_MILLION, MILLION_PLUS
    }

    private Map<ComplexityRange, Integer> countMap = new EnumMap<>(ComplexityRange.class);
    private Map<ComplexityRange, Long> timeMap = new EnumMap<>(ComplexityRange.class);

    public ComplexityProcessor() {
        for (ComplexityRange range : ComplexityRange.values()) {
            countMap.put(range, 0);
            timeMap.put(range, 0L);
        }
    }

    private ComplexityRange determineRange(int complexity) {
        if (complexity == 0) return ComplexityRange.ZERO;
        if (complexity <= 20) return ComplexityRange.ONE_TO_TWENTY;
        if (complexity <= 100) return ComplexityRange.TWENTY_ONE_TO_HUNDRED;
        if (complexity <= 500) return ComplexityRange.HUNDRED_ONE_TO_FIVE_HUNDRED;
        if (complexity <= 2000) return ComplexityRange.FIVE_HUNDRED_ONE_TO_TWO_THOUSAND;
        if (complexity <= 5000) return ComplexityRange.TWO_THOUSAND_ONE_TO_FIVE_THOUSAND;
        if (complexity <= 20000) return ComplexityRange.FIVE_THOUSAND_ONE_TO_TWENTY_THOUSAND;
        if (complexity <= 100000) return ComplexityRange.TWENTY_THOUSAND_TO_HUNDRED_THOUSAND;
        if (complexity <= 1000000) return ComplexityRange.HUNDRED_THOUSAND_TO_MILLION;
        return ComplexityRange.MILLION_PLUS;
    }

    public void record(int complexity) {
        ComplexityRange range = determineRange(complexity);
        countMap.put(range, countMap.get(range) + 1);
    }

    public void recordTime(int complexity, long time) {
        ComplexityRange range = determineRange(complexity);
        timeMap.put(range, timeMap.get(range) + time);
    }

    private void printCountForRange(ComplexityRange range, String label) {
        System.out.println(label + ": " + countMap.get(range));
    }

    private void printTimeForRange(ComplexityRange range, String label) {
        int count = countMap.get(range);
        long time = timeMap.get(range);
        System.out.println(label + "区间的过程间分析总用时: " + time);
        System.out.println(label + "区间的过程间分析平均用时: " + (count == 0 ? 0 : time / count));
    }

    public void print() {
        System.out.println("Complexity Distribution:");
        for (ComplexityRange range : ComplexityRange.values()) {
            printCountForRange(range, range.name());
        }
    }

    public void printTime() {
        System.out.println("Time Distribution:");
        for (ComplexityRange range : ComplexityRange.values()) {
            printTimeForRange(range, range.name());
        }
    }
}
