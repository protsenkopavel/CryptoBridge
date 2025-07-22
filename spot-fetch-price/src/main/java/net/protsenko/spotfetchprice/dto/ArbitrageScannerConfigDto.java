package net.protsenko.spotfetchprice.dto;

import java.util.List;

public record ArbitrageScannerConfigDto(
        List<String> pairsToScan,
        List<String> exchangesToScan,
        double minVolume,
        double minProfitPercent
) {
}