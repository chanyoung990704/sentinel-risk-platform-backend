package com.aivle.project.company.reportjob.adapter;
import com.aivle.project.company.reportjob.model.*;
import java.nio.file.Path;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import java.nio.file.*;
import java.io.IOException;
import java.util.UUID;
import com.aivle.project.dataimport.service.ImportPreflightService;
@Component public class LocalReportArtifactStore {
	private final Path root;private final ReportPdfValidator validator;
	public LocalReportArtifactStore(@Value("${app.file.upload.upload-dir}") String directory,ReportPdfValidator validator){root=Path.of(directory).toAbsolutePath().normalize();this.validator=validator;}
	public Path root(){return root;}
	public static String key(JobLease l){return "custom-reports/"+l.requestId()+"/"+l.generation()+"/"+l.attemptId()+".pdf";}
	public static boolean validKey(String key){return key!=null&&key.matches("custom-reports/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/[1-9][0-9]{0,8}/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.pdf");}
	public StoredReport storeAttempt(JobLease lease,GeneratedReport report){
		if(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("File I/O requires no transaction");
		byte[] bytes=report.bytes();validator.validate(bytes);String hash=ImportPreflightService.sha256(bytes);if(!hash.equals(report.sha256()))throw new ReportGenerationFailure("PDF_CHECKSUM",false);
		String key=key(lease);Path target=path(key),temp=null;
		try{
			createParents(target.getParent());path(key);temp=target.resolveSibling(target.getFileName()+"."+UUID.randomUUID()+".pending");
			try(var output=java.nio.channels.FileChannel.open(temp,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)){var buffer=java.nio.ByteBuffer.wrap(bytes);while(buffer.hasRemaining())output.write(buffer);output.force(true);}
			// 완전히 기록한 inode를 새 이름으로 연결한다. 기존 결과는 덮어쓰지 않는다.
			try{Files.createLink(target,temp);}catch(FileAlreadyExistsException duplicate){if(!hash.equals(ImportPreflightService.sha256(read(key))))throw new ReportGenerationFailure("ARTIFACT_CONFLICT",false);}
			return new StoredReport(lease.requestId(),lease.generation(),lease.attemptId(),key,bytes.length,hash);
		}catch(IOException failure){throw new ReportGenerationFailure("FILE_IO",true);}finally{if(temp!=null)try{Files.deleteIfExists(temp);}catch(IOException ignored){/* 유예 후 고아 정리 대상 */}}
	}
	public Path path(String key){if(!validKey(key))throw new ReportGenerationFailure("FILE_KEY",false);Path target=root.resolve(key).normalize();checkLinks(target);return target;}
	private void checkLinks(Path target){Path cursor=target.getRoot();for(Path part:target){cursor=cursor.resolve(part);if(Files.isSymbolicLink(cursor))throw new ReportGenerationFailure("FILE_SYMLINK",false);}}
	private void createParents(Path parent)throws IOException {Path cursor=parent.getRoot();for(Path part:parent){cursor=cursor.resolve(part);if(Files.isSymbolicLink(cursor))throw new ReportGenerationFailure("FILE_SYMLINK",false);if(!Files.exists(cursor,LinkOption.NOFOLLOW_LINKS))try{Files.createDirectory(cursor);}catch(FileAlreadyExistsException race){if(!Files.isDirectory(cursor,LinkOption.NOFOLLOW_LINKS))throw race;}if(!Files.isDirectory(cursor,LinkOption.NOFOLLOW_LINKS))throw new IOException("Invalid storage directory");}}
	public byte[] read(String key){Path target=path(key);try(var input=Files.newInputStream(target,LinkOption.NOFOLLOW_LINKS)){byte[] bytes=input.readNBytes(ReportPdfValidator.MAX_BYTES+1);if(bytes.length>ReportPdfValidator.MAX_BYTES)throw new ReportGenerationFailure("PDF_SIZE",false);return bytes;}catch(IOException failure){throw new ReportGenerationFailure("FILE_IO",true);}}
}
