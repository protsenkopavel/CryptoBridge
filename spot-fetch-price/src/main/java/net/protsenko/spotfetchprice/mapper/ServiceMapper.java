package net.protsenko.spotfetchprice.mapper;

import net.protsenko.spotfetchprice.dto.PriceSpreadResult;
import net.protsenko.spotfetchprice.dto.PriceSpreadResultDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ServiceMapper extends Mappable<PriceSpreadResult, PriceSpreadResultDTO>{
}
