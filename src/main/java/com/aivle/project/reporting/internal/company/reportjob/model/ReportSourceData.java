package com.aivle.project.reporting.internal.company.reportjob.model;
import com.aivle.project.finance.api.CanonicalMetricCell;
import java.util.*;
public record ReportSourceData(UUID snapshotId,Long companyId,int targetQuarterKey,List<CanonicalMetricCell> cells,String checksum) {
	public ReportSourceData {cells=List.copyOf(cells);}
}
