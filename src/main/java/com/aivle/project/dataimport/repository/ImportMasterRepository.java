package com.aivle.project.dataimport.repository;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 마스터 적재의 SQL 접근. 충돌 판정과 transaction은 service가 담당한다. */
@Repository
public class ImportMasterRepository {
	private final JdbcTemplate jdbc;
	public ImportMasterRepository(JdbcTemplate jdbc) {this.jdbc=jdbc;}
	public List<Map<String,Object>> lockIndustry(String code) {return jdbc.queryForList("SELECT id,industry_name,deleted_at FROM industry_codes WHERE industry_code=? FOR UPDATE",code);}
	public void insertIndustry(String code,String name) {jdbc.update("INSERT INTO industry_codes(industry_code,industry_name) VALUES(?,?)",code,name);}
	public void enrichIndustry(Object id,String name) {jdbc.update("UPDATE industry_codes SET industry_name=? WHERE id=?",name,id);}
	public long industryId(String code) {return jdbc.queryForObject("SELECT id FROM industry_codes WHERE industry_code=?",Long.class,code);}
	public List<Map<String,Object>> lockCompanies(String stock,String corp) {return jdbc.queryForList("SELECT corp_code,corp_name,stock_code,deleted_at FROM companies WHERE stock_code=? OR corp_code=? FOR UPDATE",stock,corp);}
	public void insertCompany(String corp,String name,String stock) {jdbc.update("INSERT INTO companies(corp_code,corp_name,stock_code) VALUES(?,?,?)",corp,name,stock);}
	public boolean bindIndustry(String stock,long industry) {return jdbc.update("UPDATE companies SET industry_code_id=? WHERE stock_code=? AND (industry_code_id IS NULL OR industry_code_id=?)",industry,stock,industry)==1;}
}
