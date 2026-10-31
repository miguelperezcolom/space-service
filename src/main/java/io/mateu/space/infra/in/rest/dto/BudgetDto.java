package io.mateu.space.infra.in.rest.dto;

import java.math.BigDecimal;

/** One budget line: a program's money in a fiscal year, in millions of US dollars. */
public record BudgetDto(
    Long id,
    Integer fiscalYear,
    String program,
    String category,
    BigDecimal amountMillionsUsd,
    Boolean synthetic,
    String basis,
    String url) {}
