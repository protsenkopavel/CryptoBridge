package net.protsenko.spotfetchprice.mapper;

import net.protsenko.spotfetchprice.dto.ArbitrageScannerConfigDto;
import net.protsenko.spotfetchprice.service.ArbitrageScannerConfig;
import org.knowm.xchange.currency.CurrencyPair;
import org.mapstruct.Mapper;

import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface ArbitrageScannerConfigMapper extends Mappable<ArbitrageScannerConfig, ArbitrageScannerConfigDto> {
}