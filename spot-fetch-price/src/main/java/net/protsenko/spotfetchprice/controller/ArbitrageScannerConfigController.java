package net.protsenko.spotfetchprice.controller;

import lombok.RequiredArgsConstructor;
import net.protsenko.spotfetchprice.dto.ArbitrageScannerConfigDto;
import net.protsenko.spotfetchprice.mapper.ArbitrageScannerConfigMapper;
import net.protsenko.spotfetchprice.service.ArbitrageScannerConfig;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/scanner/config")
@RequiredArgsConstructor
public class ArbitrageScannerConfigController {

    private final ArbitrageScannerConfig config;
    private final ArbitrageScannerConfigMapper mapper;

    @GetMapping
    public ArbitrageScannerConfigDto getConfig() {
        return mapper.toDto(config);
    }

    @PostMapping
    public void setConfig(@RequestBody ArbitrageScannerConfigDto dto) {
        ArbitrageScannerConfig newConfig = mapper.toEntity(dto);
        config.setPairsToScan(newConfig.getPairsToScan());
        config.setExchangesToScan(newConfig.getExchangesToScan());
        config.setMinVolume(newConfig.getMinVolume());
        config.setMinProfitPercent(newConfig.getMinProfitPercent());
    }
}
