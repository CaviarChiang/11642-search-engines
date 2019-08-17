import java.util.*;

/**
 * Utility class for sorting map by value.
 * Referenced web resources:
 *  [Sort a Map by values](https://stackoverflow.com/questions/109383/sort-a-mapkey-value-by-values)
 */
public class MapUtil {

    /**
     * Sort map by descending value.
     *
     * @param map Map to sort
     * @param maxEntry Maximum number of entries we want to keep in the map
     * @param <K> Type of key
     * @param <V> Type of value
     * @return
     */
    public static <K, V extends Comparable<? super V>> Map<K, V> sortByDescValue(Map<K, V> map, int maxEntry) {
        // Copy over the entry set of map to a new array list, then sort the array list by entry's value
        List<Map.Entry<K, V>> list = new ArrayList<>(map.entrySet());
        list.sort(Collections.reverseOrder(Map.Entry.comparingByValue()));

        // Put the entries in the sorted array list into a linked hash map (insertion order = sorted order)
        Map<K, V> result = new LinkedHashMap<>();
        for (int i = 0; i < Math.min(list.size(), maxEntry); i++) {
            result.put(list.get(i).getKey(), list.get(i).getValue());
        }

        return result;
    }

}
