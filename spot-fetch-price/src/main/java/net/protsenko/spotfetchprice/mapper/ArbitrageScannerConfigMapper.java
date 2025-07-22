package net.protsenko.spotfetchprice.mapper;

import net.protsenko.spotfetchprice.dto.ArbitrageScannerConfigDto;
import net.protsenko.spotfetchprice.service.ArbitrageScannerConfig;
import org.knowm.xchange.currency.CurrencyPair;
import org.mapstruct.Mapper;

import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface ArbitrageScannerConfigMapper extends Mappable<ArbitrageScannerConfig, ArbitrageScannerConfigDto> {

    default String map(CurrencyPair pair) {
        return pair == null ? null : pair.toString();
    }

    default CurrencyPair map(String pair) {
        return pair == null ? null : new CurrencyPair(pair);
    }

    default List<String> mapPairsToString(List<CurrencyPair> pairs) {
        if (pairs == null) return null;
        return pairs.stream().map(this::map).collect(Collectors.toList());
    }

    default List<CurrencyPair> mapStringsToPairs(List<String> pairs) {
        if (pairs == null) return null;
        return pairs.stream().map(this::map).collect(Collectors.toList());
    }

}