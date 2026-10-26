package org.example.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 🌍 GitRepoService
 * Servicio para clonar y analizar repositorios Git.
 *
 * <p>Solo acepta URLs {@code https://} de hosts permitidos. El comando se construye como lista
 * de argumentos (sin shell), con {@code --} delante de la URL para que nunca se interprete como
 * opción, y limitando los protocolos de git a https. Los procesos tienen tiempo máximo.</p>
 */
public class GitRepoService {

    private static final Logger LOGGER = Logger.getLogger(GitRepoService.class.getName());
    private static final String TEMP_DIR = System.getProperty("java.io.tmpdir");
    private static final String CLONE_PREFIX = "relation-analyzer-";

    /** Hosts permitidos por defecto. Se pueden cambiar con la variable RELATIONS_GIT_ALLOWED_HOSTS. */
    public static final Set<String> DEFAULT_ALLOWED_HOSTS = Set.of("github.com", "gitlab.com", "bitbucket.org");
    static final String ALLOWED_HOSTS_ENV = "RELATIONS_GIT_ALLOWED_HOSTS";

    private static final Pattern SAFE_PATH = Pattern.compile("^/[A-Za-z0-9._~/-]+$");
    private static final Pattern REPO_NAME_CLEAN = Pattern.compile("[^a-zA-Z0-9-_]");

    static final Duration DEFAULT_CLONE_TIMEOUT = Duration.ofMinutes(5);
    static final Duration VERSION_TIMEOUT = Duration.ofSeconds(10);

    private final Set<String> allowedHosts;
    private final Duration cloneTimeout;
    private final String gitExecutable;

    public GitRepoService() {
        this(allowedHostsFromEnv(), DEFAULT_CLONE_TIMEOUT, "git");
    }

    /**
     * @param allowedHosts hosts aceptados en las URLs (se comparan en minúsculas)
     * @param cloneTimeout tiempo máximo del {@code git clone}
     * @param gitExecutable ejecutable de git (normalmente "git")
     */
    public GitRepoService(Set<String> allowedHosts, Duration cloneTimeout, String gitExecutable) {
        this.allowedHosts = allowedHosts.stream()
            .map(h -> h.trim().toLowerCase(Locale.ROOT))
            .filter(h -> !h.isEmpty())
            .collect(Collectors.toUnmodifiableSet());
        this.cloneTimeout = cloneTimeout;
        this.gitExecutable = gitExecutable;
    }

    private static Set<String> allowedHostsFromEnv() {
        String env = System.getenv(ALLOWED_HOSTS_ENV);
        if (env == null || env.isBlank()) {
            return DEFAULT_ALLOWED_HOSTS;
        }
        return Arrays.stream(env.split(",")).map(String::trim).filter(s -> !s.isEmpty())
            .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Valida una URL de repositorio.
     *
     * @param repoUrl URL introducida por el usuario
     * @return la URL normalizada si es aceptable, o vacío si no
     */
    public Optional<String> validateRepoUrl(String repoUrl) {
        if (repoUrl == null) {
            return Optional.empty();
        }
        String url = repoUrl.trim();
        if (url.isEmpty() || url.length() > 500 || !url.startsWith("https://")) {
            return Optional.empty();
        }
        for (int i = 0; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c <= ' ' || c == 0x7F) {
                return Optional.empty(); // espacios o caracteres de control
            }
        }
        try {
            URI uri = new URI(url);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null
                    || uri.getRawFragment() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)) {
                return Optional.empty();
            }
            String host = uri.getHost();
            if (host == null || !allowedHosts.contains(host.toLowerCase(Locale.ROOT))) {
                return Optional.empty();
            }
            String path = uri.getRawPath();
            if (path == null || !SAFE_PATH.matcher(path).matches() || path.contains("..")
                    || path.chars().filter(ch -> ch == '/').count() < 2) {
                return Optional.empty(); // al menos /owner/repo
            }
            return Optional.of(url);
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }

    /**
     * Construye el comando de clonado. Package-private para poder comprobarlo en tests.
     */
    List<String> buildCloneCommand(String validatedUrl, Path targetDir) {
        return List.of(
            gitExecutable,
            "-c", "protocol.allow=never",
            "-c", "protocol.https.allow=always",
            "clone", "--depth", "1", "--no-recurse-submodules",
            "--",
            validatedUrl,
            targetDir.toString());
    }

    /**
     * Clona un repositorio Git en una carpeta temporal.
     * 
     * @param repoUrl URL https del repositorio (host permitido)
     * @param progressCallback callback para reportar progreso
     * @return File apuntando al directorio clonado, o null si falla
     */
    public File cloneRepository(String repoUrl, Consumer<String> progressCallback) {
        Optional<String> validated = validateRepoUrl(repoUrl);
        if (validated.isEmpty()) {
            progressCallback.accept("❌ URL no permitida. Usa https:// y uno de estos hosts: " + allowedHosts);
            return null;
        }
        String url = validated.get();

        String repoName = extractRepoName(url);
        Path targetDir = Path.of(TEMP_DIR, CLONE_PREFIX + repoName + "-" + System.currentTimeMillis());

        Process process = null;
        try {
            Files.createDirectories(targetDir);
            progressCallback.accept("📥 Clonando " + repoName + "...");

            ProcessBuilder pb = new ProcessBuilder(buildCloneCommand(url, targetDir));
            pb.redirectErrorStream(true);
            pb.environment().put("GIT_TERMINAL_PROMPT", "0");
            pb.environment().put("GIT_ASKPASS", "");
            process = pb.start();

            // La salida se lee en otro hilo para que waitFor pueda aplicar el tiempo máximo
            Process running = process;
            Thread reader = Thread.ofVirtual().name("git-clone-output").start(() -> {
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(running.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        progressCallback.accept(line);
                    }
                } catch (IOException e) {
                    LOGGER.log(Level.FINE, "Salida de git cerrada", e);
                }
            });

            if (!process.waitFor(cloneTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                reader.join(Duration.ofSeconds(5));
                progressCallback.accept("❌ Tiempo agotado clonando el repositorio (" + cloneTimeout.toSeconds() + " s)");
                deleteDirectory(targetDir);
                return null;
            }
            reader.join(Duration.ofSeconds(5));

            int exitCode = process.exitValue();
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
            if (process != null) {
                process.destroyForcibly();
            }
            deleteDirectory(targetDir);
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

        Path root = repoDir.toPath();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isDirectory)
                 .filter(this::containsJavaFiles)
                 .filter(p -> isNotTestOrResource(root.relativize(p)))
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
        try (Stream<Path> paths = Files.walk(repoDir.toPath(), 4)) {
            Optional<File> nested = paths.filter(p -> p.endsWith(Path.of("src", "main", "java")))
                       .filter(Files::isDirectory)
                       .map(Path::toFile)
                       .findFirst();
            if (nested.isPresent()) {
                return nested.get();
            }
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
        if (repoDir != null && repoDir.exists() && repoDir.getName().startsWith(CLONE_PREFIX)) {
            deleteDirectory(repoDir.toPath());
        }
    }

    /**
     * Verifica si Git está instalado en el sistema (con tiempo máximo).
     */
    public boolean isGitAvailable() {
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(gitExecutable, "--version");
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            pb.environment().put("GIT_TERMINAL_PROMPT", "0");
            process = pb.start();
            if (!process.waitFor(VERSION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            if (process != null) {
                process.destroyForcibly();
            }
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
        String clean = REPO_NAME_CLEAN.matcher(name).replaceAll("");
        return clean.isEmpty() ? "repo" : clean;
    }

    private boolean containsJavaFiles(Path dir) {
        try (Stream<Path> files = Files.list(dir)) {
            return files.anyMatch(p -> p.toString().endsWith(".java"));
        } catch (IOException e) {
            return false;
        }
    }

    /** Se evalúa sobre la ruta relativa al repositorio para no depender de dónde esté el temporal. */
    private boolean isNotTestOrResource(Path relative) {
        for (Path part : relative) {
            String name = part.toString().toLowerCase(Locale.ROOT);
            if (name.equals("test") || name.equals("resources") || name.equals("target") || name.equals("build")) {
                return false;
            }
        }
        return true;
    }

    private void deleteDirectory(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
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
