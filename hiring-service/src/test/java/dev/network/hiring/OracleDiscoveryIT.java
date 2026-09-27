package dev.network.hiring;

import static org.assertj.core.api.Assertions.*;

import dev.network.hiring.company.CompanyService;
import dev.network.hiring.discovery.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class OracleDiscoveryIT extends OracleHiringTest {
  @Autowired CompanyDiscovery discovery;
  @Autowired CompanyFollowService follows;

  @Test
  void explicitFiltersAndFollowExclusionAreDeterministic() {
    String owner = id(), actor = id(), industry = "Industry-" + id();
    var a =
        companies.create(
            owner,
            new CompanyService.Input(
                "A", "suggest-a-" + id(), "Description", industry, "London", null));
    var b =
        companies.create(
            owner,
            new CompanyService.Input(
                "B", "suggest-b-" + id(), "Description", industry, "London", null));
    follows.follow(actor, b.id());
    var result = discovery.suggest(actor, industry.toLowerCase(), "london", 20);
    assertThat(result).extracting(CompanyDiscovery.Suggestion::companyId).containsExactly(a.id());
    assertThat(result.getFirst().reason()).isEqualTo("Industry matches; Location matches");
    assertThat(discovery.suggest(actor, industry, "london", 20)).isEqualTo(result);
    assertThat(discovery.suggest(actor, industry, "Paris", 20)).isEmpty();
  }
}
