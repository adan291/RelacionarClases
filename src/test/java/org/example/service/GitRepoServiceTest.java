package org.example.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GitRepoServiceTest {

    @TempDir
    Path tempDir;

    private final GitRepoService service =
        new GitRepoService(GitRepoService.DEFAULT_ALLOWED_HOSTS, Duration.ofSeconds(30), "git");

    @ParameterizedTest
    @ValueSource(strings = {
        "https://github.com/owner/repo.git",
        "https://github.com/owner/repo",
        "https://GitLab.com/group/sub/repo.git",
        "  https://bitbucket.org/team/repo  "
    })
    void acceptsHttpsUrlsFromAllowedHosts(String url) {
        assertTrue(service.validateRepoUrl(url).isPresent(), url);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "--upload-pack=touch /tmp/pwned",
        "-c core.sshCommand=calc",
        "ext::sh -c touch% /tmp/pwned",
        "file:///etc/passwd",
        "http://github.com/owner/repo.git",
        "git@github.com:owner/repo.git",
        "ssh://git@github.com/owner/repo.git",
        "https://evil.example.com/owner/repo.git",
        "https://github.com.evil.com/owner/repo.git",
        "https://user:token@github.com/owner/repo.git",
        "https://github.com:8443/owner/repo.git",
        "https://github.com/owner/repo.git --upload-pack=x",
        "https://github.com/owner/repo.git\n--upload-pack=x",
        "https://github.com/../../etc",
        "https://github.com/",
        "https://github.com/owner/repo?x=1",
        ""
    })
    void rejectsDangerousOrForeignUrls(String url) {
        assertTrue(service.validateRepoUrl(url).isEmpty(), url);
    }

    @Test
    void rejectsNull() {
        assertTrue(service.validateRepoUrl(null).isEmpty());
    }

    @Test
    void cloneCommandSeparatesUrlWithDoubleDashAndRestrictsProtocols() {
        Path target = tempDir.resolve("clone");
        List<String> cmd = service.buildCloneCommand("https://github.com/owner/repo.git", target);

        int dashDash = cmd.indexOf("--");
        assertTrue(dashDash > 0, "Debe haber un '--' antes de la URL");
        assertEquals("https://github.com/owner/repo.git", cmd.get(dashDash + 1));
        assertEquals(target.toString(), cmd.get(dashDash + 2));
        assertEquals(dashDash + 3, cmd.size(), "Nada después del destino");
        assertTrue(cmd.subList(0, cmd.indexOf("clone")).containsAll(
            List.of("-c", "protocol.allow=never", "protocol.https.allow=always")));
    }

    @Test
    void cloneRejectsInvalidUrlWithoutStartingProcess() {
        List<String> messages = new ArrayList<>();
        GitRepoService noGit = new GitRepoService(Set.of("github.com"), Duration.ofSeconds(1),
            "this-git-binary-does-not-exist");

        File result = noGit.cloneRepository("--upload-pack=calc", messages::add);

        assertNull(result);
        assertTrue(messages.get(0).contains("URL no permitida"), messages.toString());
    }

    @Test
    void isGitAvailableReturnsFalseForMissingBinary() {
        GitRepoService noGit = new GitRepoService(Set.of("github.com"), Duration.ofSeconds(1),
            "this-git-binary-does-not-exist");
        assertFalse(noGit.isGitAvailable());
    }

    @Test
    void findMainSourceFolderPrefersRootSrcMainJava() throws IOException {
        Path src = Files.createDirectories(tempDir.resolve("src/main/java/com/acme"));
        Files.writeString(src.resolve("A.java"), "class A {}");

        assertEquals(tempDir.resolve("src/main/java").toFile(), service.findMainSourceFolder(tempDir.toFile()));
    }

    @Test
    void findMainSourceFolderFindsModuleSrcMainJava() throws IOException {
        Path src = Files.createDirectories(tempDir.resolve("module-a/src/main/java"));
        Files.writeString(src.resolve("A.java"), "class A {}");

        assertEquals(src.toFile(), service.findMainSourceFolder(tempDir.toFile()));
    }

    @Test
    void findMainSourceFolderFallsBackToFirstJavaFolder() throws IOException {
        Path code = Files.createDirectories(tempDir.resolve("code/model"));
        Files.writeString(code.resolve("User.java"), "class User {}");
        Path tests = Files.createDirectories(tempDir.resolve("test/model"));
        Files.writeString(tests.resolve("UserTest.java"), "class UserTest {}");

        assertEquals(code.toFile(), service.findMainSourceFolder(tempDir.toFile()),
            "Sin src/main/java debe usar la primera carpeta con .java que no sea de test");
    }

    @Test
    void findMainSourceFolderReturnsNullWithoutJavaFiles() throws IOException {
        Files.writeString(tempDir.resolve("README.md"), "hola");
        assertNull(service.findMainSourceFolder(tempDir.toFile()));
    }
}
