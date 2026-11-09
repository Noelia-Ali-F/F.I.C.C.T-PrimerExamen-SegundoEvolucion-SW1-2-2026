package com.arqnova.servicios.impl;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Conservative safety shared by group modeling and the original text/voice interpreter. */
public final class UmlModelingIntent {
    private UmlModelingIntent() {}
    private static final Pattern UNCERTAIN=Pattern.compile(
            "\\b(?:o bien|tal vez|quizas|podriamos|si (?:conviene|te parece|crees|consideras|es necesario|hace falta)|"
            + "(?:lo|la) que (?:quieras|prefieras)|(?:todavia|aun) no|"
            + "no (?:lo |la |los |las )?(?:hagas|haga|hagan|apliques|aplique|ejecutes|modifiques|edites|cambies|crees|agregues|elimines|borres)|"
            + "sin (?:hacer|aplicar|ejecutar|modificar|editar|cambiar|crear|borrar|eliminar))\\b|\\s+o\\s+|\\balgo\\s*[.!?]*$");
    public static boolean needsClarification(String instruction){
        if(instruction==null)return true;
        String value=Normalizer.normalize(instruction,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replaceAll("\\s+"," ");
        value=value.replaceAll("\\b(?:cero|uno) o (?:uno|muchos|varios|mas)\\b","multiplicidad");
        return UNCERTAIN.matcher(value).find();
    }
}
