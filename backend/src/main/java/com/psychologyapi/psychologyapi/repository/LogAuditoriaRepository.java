package com.psychologyapi.psychologyapi.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.psychologyapi.psychologyapi.model.Usuario;
import com.psychologyapi.psychologyapi.model.entity.LogAuditoria;

public interface LogAuditoriaRepository extends JpaRepository<LogAuditoria, UUID>{

    Page<LogAuditoria> findByUsuarioOrderByCreatedAtDesc(Usuario usuario, Pageable pageable);

    Page<LogAuditoria> findByUsuarioAndEntidadeOrderByCreatedAtDesc(Usuario usuario, String entidade,
            Pageable pageable);

    Page<LogAuditoria> findByUsuarioAndEntidadeAndEntidadeIdOrderByCreatedAtDesc(Usuario usuario, String entidade,
            UUID entidadeId, Pageable pageable);

}
