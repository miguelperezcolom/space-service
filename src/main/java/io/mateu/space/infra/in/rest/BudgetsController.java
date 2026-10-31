package io.mateu.space.infra.in.rest;

import io.mateu.space.domain.BudgetLine;
import io.mateu.space.infra.in.rest.dto.BudgetDto;
import io.mateu.space.infra.in.rest.query.FilterKind;
import io.mateu.space.infra.in.rest.query.QuerySpec;
import io.mateu.space.infra.out.BudgetLineRepository;
import io.mateu.space.infra.out.SpaceRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Budget lines: a program's money in a fiscal year. The yearly totals follow Wikidata's NASA budget;
 * the split by program is SYNTHETIC (see {@link BudgetLine}).
 */
@RestController
@RequestMapping("/api/budgets")
@Tag(name = "Budget", description = "Millions of USD per program and fiscal year (split synthetic)")
public class BudgetsController extends CrudController<BudgetLine, BudgetDto> {

  static final List<String> CATEGORIES = List.of("SCIENCE", "EXPLORATION", "OPERATIONS",
      "TECHNOLOGY", "AERONAUTICS", "EDUCATION", "SUPPORT");

  private final BudgetLineRepository budgets;

  public BudgetsController(BudgetLineRepository budgets) {
    this.budgets = budgets;
  }

  @Override
  protected String collection() {
    return "budgets";
  }

  @Override
  protected SpaceRepository<BudgetLine> repository() {
    return budgets;
  }

  @Override
  protected QuerySpec querySpec() {
    return QuerySpec.builder()
        .filter("program", FilterKind.EXACT)
        .filter("category", FilterKind.EXACT)
        .filter("fiscalYear", FilterKind.NUMBER)
        .filter("amountMillionsUsd", FilterKind.NUMBER)
        .filter("synthetic", FilterKind.BOOLEAN)
        .searchable("program", "basis")
        .defaultSort("id")
        .build();
  }

  @Override
  protected BudgetLine newEntity() {
    return new BudgetLine();
  }

  @Override
  protected Long idOf(BudgetDto dto) {
    return dto.id();
  }

  @Override
  protected BudgetDto toDto(BudgetLine b) {
    return new BudgetDto(b.getId(), b.getFiscalYear(), b.getProgram(), b.getCategory(),
        b.getAmountMillionsUsd(), b.getSynthetic(), b.getBasis(), urlOf(b.getId()));
  }

  @Override
  protected void apply(BudgetDto dto, BudgetLine b) {
    b.setFiscalYear(dto.fiscalYear());
    b.setProgram(dto.program());
    b.setCategory(oneOf(dto.category(), "category", CATEGORIES));
    b.setAmountMillionsUsd(dto.amountMillionsUsd());
    b.setSynthetic(dto.synthetic() == null || dto.synthetic());
    b.setBasis(dto.basis());
  }
}
