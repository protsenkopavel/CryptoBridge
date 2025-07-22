package net.protsenko.spotfetchprice.controller;

import lombok.RequiredArgsConstructor;
import net.protsenko.spotfetchprice.dto.PriceSpreadResultDTO;
import net.protsenko.spotfetchprice.dto.SpreadsRq;
import net.protsenko.spotfetchprice.mapper.ServiceMapper;
import net.protsenko.spotfetchprice.service.PriceSpreadService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/spreads")
public class PriceSpreadController {

    private final PriceSpreadService priceSpreadService;
    private final ServiceMapper serviceMapper;

    @PostMapping("/best-spreads")
    public List<PriceSpreadResultDTO> getSpreadsByExchangerAndTicker(@RequestBody @Validated SpreadsRq spreadsRq) {
        return serviceMapper.toDto(priceSpreadService.findMaxArbitrageSpreadsForPairs(spreadsRq));
    }

}
