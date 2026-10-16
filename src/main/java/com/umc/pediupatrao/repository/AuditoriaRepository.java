package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.AuditoriaEvento;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface AuditoriaRepository extends Repository<AuditoriaEvento, String> {

    AuditoriaEvento save(AuditoriaEvento evento);

    Optional<AuditoriaEvento> findById(String id);

    List<AuditoriaEvento> findAllByOrderByDataHoraDesc();
}
