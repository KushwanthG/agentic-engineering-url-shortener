package com.agentic.sdlc.orchestration.audit;

import java.util.List;

import org.springframework.data.repository.Repository;

/** Insert and read only: the audit trail has no update or delete path in the application. */
public interface AuditEventRepository extends Repository<AuditEvent, Long> {

    AuditEvent save(AuditEvent event);

    List<AuditEvent> findByChainIdOrderBySeqAsc(String chainId);
}
