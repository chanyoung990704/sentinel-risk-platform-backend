package com.aivle.project.community.internal;
import com.aivle.project.community.api.*;
import com.aivle.project.community.internal.category.repository.CategoriesRepository;
import com.aivle.project.community.internal.category.mapper.CategoryMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@RequiredArgsConstructor
class CategoryQueryService implements CategoryQueries {
	private final CategoriesRepository categories;
	private final CategoryMapper mapper;
	@Transactional(readOnly = true) public List<CategorySummaryResponse> list() {
		return categories.findAllByDeletedAtIsNullOrderBySortOrderAscIdAsc().stream().map(mapper::toSummaryResponse).toList();
	}
}
