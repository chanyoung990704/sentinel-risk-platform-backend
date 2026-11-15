package com.aivle.project.finance.internal;
import com.aivle.project.finance.api.*;
import com.aivle.project.finance.internal.company.repository.CompaniesRepository;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class CompanyDirectoryService implements CompanyDirectory {
	private final CompaniesRepository companies;
	public Optional<CompanyIdentity> findById(Long id) { return companies.findDirectoryById(id).map(CompanyView::copyOf); }
	public Optional<CompanyIdentity> findByStockCode(String code) { return companies.findDirectoryByStockCode(code).map(CompanyView::copyOf); }
	public boolean existsById(Long id) { return companies.existsById(id); }
	public List<CompanyIdentity> findAllById(Iterable<Long> ids) {
		return companies.findDirectoryByIds(ids).stream().map(company -> (CompanyIdentity) CompanyView.copyOf(company)).toList();
	}
}
