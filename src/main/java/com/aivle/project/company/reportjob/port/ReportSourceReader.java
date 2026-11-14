package com.aivle.project.company.reportjob.port;
import com.aivle.project.company.reportjob.model.ReportSourceData;
import java.util.UUID;
import java.util.List;
import com.aivle.project.dataimport.repository.SnapshotReportRepository;
import com.aivle.project.dataimport.model.CanonicalMetricCell;
import com.aivle.project.dataimport.service.ImportPreflightService;
import com.aivle.project.quarter.support.QuarterCalculator;
import com.aivle.project.company.reportjob.model.ReportGenerationFailure;
import org.springframework.stereotype.Component;
@Component public class ReportSourceReader {
	private final SnapshotReportRepository reports;
	public ReportSourceReader(SnapshotReportRepository reports){this.reports=reports;}
	public ReportSourceData load(UUID snapshotId,Long companyId,int targetQuarterKey){
		if(snapshotId==null||companyId==null||!reports.state(snapshotId).equals("VERIFIED"))throw new ReportGenerationFailure("SOURCE_INVALID",false);
		int from=QuarterCalculator.offset(QuarterCalculator.parseQuarterKey(targetQuarterKey),-3).toQuarterKey();
		// 로컬 원본 경로/시트 출처는 모델에 보내지 않으며 값과 key의 정확한 checksum은 유지한다.
		List<CanonicalMetricCell> cells=reports.materializedRange(snapshotId,companyId,from,targetQuarterKey).stream().filter(c->c.key().valueType().equals("ACTUAL")).map(c->new CanonicalMetricCell(c.key(),c.value(),List.of())).toList();
		if(cells.stream().noneMatch(c->c.key().quarterKey()==targetQuarterKey&&c.value()!=null))throw new ReportGenerationFailure("SOURCE_EMPTY",false);
		return new ReportSourceData(snapshotId,companyId,targetQuarterKey,cells,ImportPreflightService.checksum(cells));
	}
}
