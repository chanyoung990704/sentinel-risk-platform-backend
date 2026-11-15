package com.aivle.project.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivle.project.finance.api.*;
import com.aivle.project.identity.api.CurrentActor;
import com.aivle.project.watchlist.internal.entity.CompanyWatchlistEntity;
import com.aivle.project.watchlist.internal.repository.CompanyWatchlistRepository;
import com.aivle.project.watchlist.internal.service.CompanyWatchlistService;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class QueryCompositionContractTest {
	@Test void twentyCompaniesDoNotCreateOneFinanceApiChainPerCompany() {
		// given
		var repository = mock(CompanyWatchlistRepository.class);
		var directory = mock(CompanyDirectory.class);
		var references = mock(FinancialReferenceQueries.class);
		var information = mock(CompanyInformationBatches.class);
		var service = new CompanyWatchlistService(repository, directory, references, information,
			mock(ApplicationEventPublisher.class));
		List<CompanyIdentity> companies = new ArrayList<>();
		for (long id = 1; id <= 20; id++) companies.add(new CompanyView(id, "기업" + id, "코드" + id, String.format("%06d", id)));
		var rows = companies.stream().map(company -> CompanyWatchlistEntity.create(new CurrentActor(7L, null, "사용자"), company, null)).toList();
		when(repository.findActiveByUserId(7L)).thenReturn(rows);
		var ids = companies.stream().map(CompanyIdentity::getId).toList();
		when(information.latest(ids)).thenReturn(companies.stream().map(company ->
			new CompanyInfoDto(company.getId(),company.getCorpName(),null,new CompanySectorDto("",""),null,null,null,null)).toList());
		// when
		var result = service.getWatchlistCompanies(7L);
		long financeCalls = List.of(directory, references, information).stream()
			.mapToLong(port -> mockingDetails(port).getInvocations().size()).sum();
		// then
		assertThat(result.stream().map(CompanyInfoDto::getId)).containsExactlyElementsOf(companies.stream().map(CompanyIdentity::getId).toList());
		assertThat(financeCalls).as("20개 기업의 finance 공개 API 호출 수").isLessThanOrEqualTo(2);
		verify(information).latest(ids);
		verifyNoInteractions(directory,references);
	}
}
