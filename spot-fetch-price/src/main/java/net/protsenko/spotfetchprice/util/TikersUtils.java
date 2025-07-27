package net.protsenko.spotfetchprice.util;

import java.util.Collections;
import java.util.List;

public class TikersUtils {

    public static <T> List<List<T>> partition(List<T> list, int size) {
        if (list == null || list.isEmpty() || size <= 0) {
            return Collections.emptyList();
        }
        int fullChunks = list.size() / size;
        int rest = list.size() % size;
        int total = rest == 0 ? fullChunks : fullChunks + 1;
        List<List<T>> chunks = new java.util.ArrayList<>(total);
        for (int i = 0; i < list.size(); i += size) {
            chunks.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return chunks;
    }

}
