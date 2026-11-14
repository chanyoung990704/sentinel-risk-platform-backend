package com.aivle.project.company.reportjob.model;
import com.aivle.project.dataimport.model.CanonicalMetricCell;
import java.util.*;
public record ReportSourceData(UUID snapshotId,Long companyId,int targetQuarterKey,List<CanonicalMetricCell> cells,String checksum) {
	public ReportSourceData {cells=List.copyOf(cells);}
}
