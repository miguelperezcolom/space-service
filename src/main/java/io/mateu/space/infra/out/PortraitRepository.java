package io.mateu.space.infra.out;

import io.mateu.space.domain.Portrait;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortraitRepository extends JpaRepository<Portrait, Long> {}
