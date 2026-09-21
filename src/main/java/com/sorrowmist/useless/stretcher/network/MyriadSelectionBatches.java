package com.sorrowmist.useless.stretcher.network;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Bound each list packet by encoded bytes, not by the total number of selected molds. */
public final class MyriadSelectionBatches {
    private static final int MAX_BYTES = 8_000;

    private MyriadSelectionBatches() { }

    public static List<List<String>> split(List<String> values) {
        List<List<String>> result = new ArrayList<>();
        List<String> batch = new ArrayList<>();
        int bytes = 0;
        for (String value : values) {
            int length = value.getBytes(StandardCharsets.UTF_8).length + 5;
            if (!batch.isEmpty() && bytes + length > MAX_BYTES) {
                result.add(List.copyOf(batch));
                batch.clear();
                bytes = 0;
            }
            batch.add(value);
            bytes += length;
        }
        if (!batch.isEmpty() || result.isEmpty()) result.add(List.copyOf(batch));
        return result;
    }
}
