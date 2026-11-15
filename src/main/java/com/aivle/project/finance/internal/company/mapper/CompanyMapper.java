package com.aivle.project.finance.internal.company.mapper;

import com.aivle.project.finance.api.CompanySearchResponse;
import com.aivle.project.finance.internal.company.entity.CompaniesEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface CompanyMapper {

	// 검색 응답은 내부 id를 companyId로 노출한다.
	@Mapping(target = "companyId", source = "id")
	CompanySearchResponse toSearchResponse(CompaniesEntity company);
}
