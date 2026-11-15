package com.aivle.project.reporting.internal.company.reportjob.port;
import com.aivle.project.reporting.internal.company.reportjob.model.ReportSourceData;
import java.util.UUID;
import java.util.List;
import com.aivle.project.finance.api.FinancialSources;
import com.aivle.project.finance.api.FinancialSourceException;
import com.aivle.project.finance.api.SourceRef;
import com.aivle.project.reporting.internal.company.reportjob.model.ReportGenerationFailure;
import org.springframework.stereotype.Component;
@Component public class ReportSourceReader {
	private final FinancialSources sources;
	public ReportSourceReader(FinancialSources sources){this.sources=sources;}
	public ReportSourceData load(UUID snapshotId,Long companyId,int targetQuarterKey){
		if(snapshotId==null||companyId==null)throw new ReportGenerationFailure("SOURCE_INVALID",false);
		try {
			var snapshot=sources.read(new SourceRef(snapshotId,companyId,targetQuarterKey));
			return new ReportSourceData(snapshotId,companyId,targetQuarterKey,snapshot.cells(),snapshot.checksum());
		} catch(FinancialSourceException invalid) {throw new ReportGenerationFailure(invalid.getMessage(),false);}
	}
}
