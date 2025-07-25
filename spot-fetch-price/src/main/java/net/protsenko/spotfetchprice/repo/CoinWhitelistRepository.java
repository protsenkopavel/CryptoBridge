package net.protsenko.spotfetchprice.repo;

import net.protsenko.spotfetchprice.entity.CoinWhitelist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CoinWhitelistRepository extends JpaRepository<CoinWhitelist, Long> {
}

