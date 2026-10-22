package com.arqnova.servicios;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.*;

class ArquitecturaCuatroCapasTest {
    private static final Path ROOT=Path.of("src/main/java/com/arqnova");
    private static final Set<String> CAPAS=Set.of("modelo","servicios","controladores","repositorios");
    @Test void todoElCodigoDeAplicacionPerteneceALasCuatroCapas() throws Exception {
        try(var files=Files.walk(ROOT)){
            for(Path file:files.filter(p->p.toString().endsWith(".java")).toList()){
                Path relative=ROOT.relativize(file);
                if(relative.toString().equals("ArqNovaApplication.java"))continue;
                assertThat(relative.getNameCount()).as(file.toString()).isGreaterThan(1);
                assertThat(CAPAS).as(file.toString()).contains(relative.getName(0).toString());
                String expected="package com.arqnova."+relative.getParent().toString().replace('\\','.').replace('/','.');
                assertThat(Files.readString(file)).as(file.toString()).contains(expected+";");
            }
        }
    }
    @Test void lasDependenciasRespetanLaDireccionEntreCapas() throws Exception {
        Map<String,Set<String>> allowed=Map.of("modelo",Set.of("modelo"),"repositorios",Set.of("repositorios","modelo"),"servicios",Set.of("servicios","repositorios","modelo"),"controladores",Set.of("controladores","servicios","modelo"));
        var references=Pattern.compile("com\\.arqnova\\.(modelo|repositorios|servicios|controladores)\\b");
        try(var files=Files.walk(ROOT)){
            for(Path file:files.filter(p->p.toString().endsWith(".java")).toList()){
                Path relative=ROOT.relativize(file);if(relative.getNameCount()<2)continue;
                String layer=relative.getName(0).toString();var matcher=references.matcher(Files.readString(file));
                while(matcher.find())assertThat(allowed.get(layer)).as(file+" depende de "+matcher.group(1)).contains(matcher.group(1));
            }
        }
    }
}
