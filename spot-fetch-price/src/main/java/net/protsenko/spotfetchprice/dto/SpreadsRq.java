package net.protsenko.spotfetchprice.dto;

import java.util.List;

public record SpreadsRq(
        List<String> pairs,
        List<String> exchanges,
        Double minVolume,
        Double minProfitPercent,
        Double maxProfitPercent,
        List<String> whitelist,
        List<String> blacklist
) {
    public SpreadsRq {
        if (minVolume == null) minVolume = 0.0;
        if (minProfitPercent == null) minProfitPercent = 0.0;
        if (maxProfitPercent == null) maxProfitPercent = Double.MAX_VALUE;
    }
}
