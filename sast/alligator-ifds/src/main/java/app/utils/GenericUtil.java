package app.utils;

import java.util.LinkedList;
import java.util.List;

/**
 * User: luca
 * Date: 2024/1/30
 * Description:
 */
public class GenericUtil {

    public static List<Integer> getSeries(int start, int size){
        List<Integer> series = new LinkedList<>();
        for(int i = 0; i < size; i++){
            series.add(start++);
        }
        return series;
    }
}
