package com.aivle.project.messaging.inbox;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository public class InboxEventRepository {
	private final JdbcTemplate jdbc;
	public InboxEventRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
	public boolean insertIfAbsent(InboxEventEntity event){return jdbc.update("INSERT IGNORE INTO inbox_events(consumer,event_id,request_id,payload_hash) VALUES(?,?,?,?)",event.consumer(),event.eventId().toString(),event.requestId().toString(),event.payloadHash())==1;}
	public InboxEventEntity lock(String consumer,UUID event){return jdbc.queryForObject("SELECT * FROM inbox_events WHERE consumer=? AND event_id=? FOR UPDATE",(r,n)->new InboxEventEntity(r.getString("consumer"),UUID.fromString(r.getString("event_id")),UUID.fromString(r.getString("request_id")),r.getString("payload_hash")),consumer,event.toString());}
}
