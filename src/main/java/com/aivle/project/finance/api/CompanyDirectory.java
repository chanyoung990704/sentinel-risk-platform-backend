package com.aivle.project.finance.api;
import java.util.List;
import java.util.Optional;
public interface CompanyDirectory {
	Optional<CompanyIdentity> findById(Long id);
	Optional<CompanyIdentity> findByStockCode(String code);
	List<CompanyIdentity> findAllById(Iterable<Long> ids);
	boolean existsById(Long id);
}
