package org.example.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * 🌍 GitRepoService
 * Servicio para clonar y analizar repositorios Git.
 */
public class GitRepoService {

    private static final Logger LOGGER = Logger.getLogger(GitRepoService.class.getName());
    private static final String TEMP_DIR = System.getProperty("java.io.tmpdir");
    private static final String CLONE_PREFIX = "relation-analyzer-";

    /**
     * Clona un repositorio Git en una carpeta temporal.
     * 
     * @param repoUrl URL del repositorio (https o ssh)
     * @param progressCallback callback para reportar progreso
     * @return File apuntando al directorio clonado, o null si falla
     */
    public File cloneRepository(String repoUrl, Consumer<String> progressCallback) {
        if (repoUrl == null || repoUrl.isBlank()) {
            return null;
        }

        String repoName = extractRepoName(repoUrl);
        Path targetDir = Path.of(TEMP_DIR, CLONE_PREFIX + repoName + "-" + System.currentTimeMillis());

        try {
            Files.createDirectories(targetDir);
            progressCallback.accept("📥 Clonando " + repoName + "...");

            ProcessBuilder pb = new ProcessBuilder("git", "clone", "--depth", "1", repoUrl, targetDir.toString());
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Leer output del proceso
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    progressCallback.accept(line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                progressCallback.accept("✅ Repositorio clonado en: " + targetDir);
                return targetDir.toFile();
            } else {
                progressCallback.accept("❌ Error clonando repositorio (código: " + exitCode + ")");
                deleteDirectory(targetDir);
                return null;
            }

        } catch (IOException | InterruptedException e) {
            LOGGER.log(Level.SEVERE, "Error clonando repositorio", e);
            progressCallback.accept("❌ Error: " + e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    /**
     * Busca carpetas con archivos Java en un repositorio.
     * 
     * @param repoDir directorio raíz del repositorio
     * @return lista de carpetas que contienen archivos .java
     */
    public List<File> findJavaSourceFolders(File repoDir) {
        List<File> javaDirs = new ArrayList<>();
        
        if (repoDir == null || !repoDir.exists()) {
            return javaDirs;
        }

        try (Stream<Path> paths = Files.walk(repoDir.toPath())) {
            paths.filter(Files::isDirectory)
                 .filter(this::containsJavaFiles)
                 .filter(this::isNotTestOrResource)
                 .map(Path::toFile)
                 .forEach(javaDirs::add);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error buscando carpetas Java", e);
        }

        return javaDirs;
    }

    /**
     * Encuentra la carpeta src/main/java típica de proyectos Maven/Gradle.
     * 
     * @param repoDir directorio raíz del repositorio
     * @return carpeta src/main/java o la primera carpeta con Java encontrada
     */
    public File findMainSourceFolder(File repoDir) {
        if (repoDir == null || !repoDir.exists()) {
            return null;
        }

        // Buscar estructura Maven/Gradle estándar
        File mavenSrc = new File(repoDir, "src/main/java");
        if (mavenSrc.exists() && mavenSrc.isDirectory()) {
            return mavenSrc;
        }

        // Buscar en subdirectorios (multi-módulo)
        try (Stream<Path> paths = Files.walk(repoDir.toPath(), 3)) {
            return paths.filter(p -> p.endsWith("src/main/java"))
                       .filter(Files::isDirectory)
                       .map(Path::toFile)
                       .findFirst()
                       .orElse(null);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error buscando src/main/java", e);
        }

        // Fallback: primera carpeta con archivos Java
        List<File> javaDirs = findJavaSourceFolders(repoDir);
        return javaDirs.isEmpty() ? null : javaDirs.get(0);
    }

    /**
     * Cuenta archivos Java en un directorio (recursivo).
     */
    public int countJavaFiles(File dir) {
        if (dir == null || !dir.exists()) {
            return 0;
        }

        try (Stream<Path> paths = Files.walk(dir.toPath())) {
            return (int) paths.filter(Files::isRegularFile)
                              .filter(p -> p.toString().endsWith(".java"))
                              .count();
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * Elimina un directorio temporal de repositorio clonado.
     */
    public void cleanupRepository(File repoDir) {
        if (repoDir != null && repoDir.exists() && repoDir.getPath().contains(CLONE_PREFIX)) {
            deleteDirectory(repoDir.toPath());
        }
    }

    /**
     * Verifica si Git está instalado en el sistema.
     */
    public boolean isGitAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "--version");
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    /**
     * Extrae el nombre del repositorio de la URL.
     */
    private String extractRepoName(String repoUrl) {
        String name = repoUrl;
        
        // Remover .git al final
        if (name.endsWith(".git")) {
            name = name.substring(0, name.length() - 4);
        }
        
        // Obtener última parte de la URL
        int lastSlash = name.lastIndexOf('/');
        if (lastSlash >= 0) {
            name = name.substring(lastSlash + 1);
        }
        
        // Limpiar caracteres especiales
        return name.replaceAll("[^a-zA-Z0-9-_]", "");
    }

    private boolean containsJavaFiles(Path dir) {
        try (Stream<Path> files = Files.list(dir)) {
            return files.anyMatch(p -> p.toString().endsWith(".java"));
        } catch (IOException e) {
            return false;
        }
    }

    private boolean isNotTestOrResource(Path dir) {
        String path = dir.toString().toLowerCase();
        return !path.contains("/test/") && 
               !path.contains("\\test\\") &&
               !path.contains("/resources/") && 
               !path.contains("\\resources\\") &&
               !path.contains("/target/") &&
               !path.contains("\\target\\") &&
               !path.contains("/build/") &&
               !path.contains("\\build\\");
    }

    private void deleteDirectory(Path dir) {
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder())
                 .forEach(p -> {
                     try {
                         Files.delete(p);
                     } catch (IOException e) {
                         LOGGER.log(Level.FINE, "No se pudo eliminar: " + p, e);
                     }
                 });
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error eliminando directorio", e);
        }
    }
}
