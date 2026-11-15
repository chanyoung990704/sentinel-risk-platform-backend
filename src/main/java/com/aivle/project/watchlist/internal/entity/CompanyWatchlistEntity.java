package com.aivle.project.watchlist.internal.entity;

import com.aivle.project.foundation.api.entity.BaseEntity;
import com.aivle.project.finance.api.CompanyIdentity;
import com.aivle.project.finance.api.CompanyView;
import com.aivle.project.identity.api.ActorIdentity;
import com.aivle.project.identity.api.CurrentActor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 사용자 관심 기업 엔티티. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "company_watchlists")
public class CompanyWatchlistEntity extends BaseEntity implements com.aivle.project.watchlist.api.WatchlistMember {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "company_id", nullable = false)
	private Long companyId;

	@jakarta.persistence.Transient
	private CompanyIdentity company;

	public ActorIdentity getUser() { return new CurrentActor(userId, null, null); }
	public void resolveCompany(CompanyIdentity value) { this.company = CompanyView.copyOf(value); }

	@Column(name = "note", length = 255)
	private String note;

	public static CompanyWatchlistEntity create(ActorIdentity user, CompanyIdentity company, String note) {
		CompanyWatchlistEntity entity = new CompanyWatchlistEntity();
		entity.userId = user.getId();
		entity.companyId = company.getId();
		entity.company = CompanyView.copyOf(company);
		entity.note = note;
		return entity;
	}

	public void updateNote(String note) {
		this.note = note;
	}
}
