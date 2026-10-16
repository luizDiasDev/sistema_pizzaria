package com.umc.pediupatrao.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

@Component("formatadorData")
public class FormatadorData {

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    public String formatar(Instant instante) {
        return instante == null ? "—" : FORMATO.format(instante.atZone(ZONA));
    }
}
