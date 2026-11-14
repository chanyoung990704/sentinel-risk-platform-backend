package com.aivle.project.company.reportjob.service;
import java.time.Instant;
import org.springframework.stereotype.Service;
@Service public class ReportJobRecovery {
	private final ReportJobClaimService claims;public ReportJobRecovery(ReportJobClaimService claims){this.claims=claims;}
	public int recover(Instant now){return claims.recover(now);}
}
