package net.protsenko.spotfetchprice.mapper;

import net.protsenko.spotfetchprice.dto.PriceSpreadResult;
import net.protsenko.spotfetchprice.dto.PriceSpreadResultDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ServiceMapper extends Mappable<PriceSpreadResult, PriceSpreadResultDTO> {

    @Override
    @Mapping(target = "instrument", expression = "java(source.instrument().toString())")
    @Mapping(target = "baseCurrency", expression = "java(source.instrument().getBase().toString())")
    @Mapping(target = "counterCurrency", expression = "java(source.instrument().getCounter().toString())")
    @Mapping(target = "spreadPercentage", source = "profitPercent")
    PriceSpreadResultDTO toDto(PriceSpreadResult source);

    @Override
    @Mapping(target = "instrument", ignore = true)
    PriceSpreadResult toEntity(PriceSpreadResultDTO dto);
}
