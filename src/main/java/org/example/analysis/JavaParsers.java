package org.example.analysis;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParserConfiguration.LanguageLevel;
import com.github.javaparser.ast.CompilationUnit;

/**
 * Punto único para crear parsers de JavaParser con un nivel de lenguaje explícito.
 *
 * <p>Sustituye a {@code StaticJavaParser}, cuya configuración es global y no es segura entre
 * hilos. {@link JavaParser} no es thread-safe, así que se crea uno por llamada (es barato).</p>
 *
 * <p>JavaParser 3.27.1 soporta como máximo {@link LanguageLevel#JAVA_21}.</p>
 */
public final class JavaParsers {

    /** Nivel de lenguaje usado en todo el proyecto (el máximo que soporta JavaParser 3.27.1). */
    public static final LanguageLevel LANGUAGE_LEVEL = LanguageLevel.JAVA_21;

    private JavaParsers() {
    }

    /** @return configuración nueva con el nivel de lenguaje del proyecto y UTF-8 */
    public static ParserConfiguration configuration() {
        return new ParserConfiguration()
            .setLanguageLevel(LANGUAGE_LEVEL)
            .setCharacterEncoding(StandardCharsets.UTF_8);
    }

    /** @return un parser nuevo (no compartir entre hilos) */
    public static JavaParser newParser() {
        return new JavaParser(configuration());
    }

    /**
     * Parsea un fichero. Lanza {@link ParseProblemException} si tiene errores, igual que
     * {@code StaticJavaParser.parse(File)}.
     */
    public static CompilationUnit parse(File file) throws FileNotFoundException {
        return unwrap(newParser().parse(file));
    }

    /** Parsea código fuente en memoria. */
    public static CompilationUnit parse(String source) {
        return unwrap(newParser().parse(source));
    }

    private static CompilationUnit unwrap(ParseResult<CompilationUnit> result) {
        if (result.isSuccessful() && result.getResult().isPresent()) {
            return result.getResult().get();
        }
        throw new ParseProblemException(result.getProblems());
    }
}
