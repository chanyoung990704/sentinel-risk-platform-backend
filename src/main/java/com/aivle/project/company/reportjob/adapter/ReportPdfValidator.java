package com.aivle.project.company.reportjob.adapter;
import com.aivle.project.company.reportjob.model.ReportGenerationFailure;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.springframework.stereotype.Component;
/** MIME만 신뢰하지 않고 제한된 크기의 PDF 구조를 엄격하게 파싱한다. 내용 품질 검증은 아니다. */
@Component public class ReportPdfValidator {
	public static final int MAX_BYTES=10*1024*1024;
	public void validate(byte[] bytes){
		if(bytes==null||bytes.length==0||bytes.length>MAX_BYTES)throw new ReportGenerationFailure("PDF_SIZE",false);
		if(bytes.length<8||!new String(bytes,0,5,StandardCharsets.US_ASCII).equals("%PDF-")||!new String(bytes,Math.max(0,bytes.length-64),Math.min(64,bytes.length),StandardCharsets.ISO_8859_1).stripTrailing().endsWith("%%EOF"))throw new ReportGenerationFailure("PDF_INVALID",false);
		try(var input=new RandomAccessReadBuffer(bytes);var document=new PDFParser(input).parse(false)) {
			if(document.isEncrypted()||document.getNumberOfPages()<1||document.getNumberOfPages()>100)throw new ReportGenerationFailure("PDF_INVALID",false);
		} catch(java.io.IOException|IllegalArgumentException invalid){throw new ReportGenerationFailure("PDF_INVALID",false);}
	}
}
