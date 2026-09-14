package betamoon.update;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/**
 * Offline contract checks for SemVer, provider selection, HTTP limits and
 * startup lifetime.
 */
public final class UpdateTrackerTest {
    public static void main(String[] args) throws Exception {
        verifyVersions();
        verifySelection();
        verifyOnce();
        verifyHttp();
        System.out.println("Update tracker contracts passed.");
    }

    private static void verifyVersions() {
        String[] ordered = {"1.0.0-0", "1.0.0-alpha", "1.0.0-alpha.1", "1.0.0-alpha.beta", "1.0.0-beta", "1.0.0-beta.2",
                "1.0.0-beta.11", "1.0.0-rc.1", "1.0.0", "1.0.1-alpha"};
        for (int i = 0; i < ordered.length; i++) {
            for (int j = 0; j < ordered.length; j++) {
                require(Integer.signum(compare(ordered[i], ordered[j])) == Integer.signum(i - j), "SemVer order");
            }
        }
        require(compare("0.6.0-a10", "0.6.0-a2") < 0, "Compact identifiers are lexical");
        require(compare("0.6.0-alpha.2", "0.6.0-alpha.10") < 0, "Dotted sequence is numeric");
        require(compare("0.6.0-A", "0.6.0-a") < 0, "ASCII case");
        require(compare("0.6.0+001", "0.6.0+002") == 0, "Build metadata ignored");
        require(compare("999999999999999999999.0.0", "99999999999999999999.0.0") > 0, "Large core");
        require(compare("1.0.0-999999999999999999999", "1.0.0-99999999999999999999") > 0, "Large prerelease");
        for (String valid : new String[]{"0.0.0", "1.2.3-0", "1.2.3-alpha.0", "1.2.3+001", "1.2.3-x-y.--"}) {
            SemanticVersion.parse(valid);
        }
        for (String invalid : new String[]{"01.2.3", "1.2", "1.2.3-alpha.01", "1.2.3-", "1.2.3+", "1.2.3-a..b",
                "1.2.3+..", "v1.2.3", " 1.2.3", "1.2.3\n", "1.2.3-\u00e4"}) {
            try {
                SemanticVersion.parse(invalid);
                throw new AssertionError("Accepted " + invalid);
            } catch (IllegalArgumentException expected) {
                // Expected strict rejection.
            }
        }
    }

    private static void verifySelection() throws Exception {
        String versions = array(modrinth("0.7.0", "B"), modrinth("0.6.0", "A"), modrinth("invalid", "Z"),
                modrinth("9.0.0", "X").replace("b1.7.3", "1.21"));
        AtomicInteger calls = new AtomicInteger();
        UpdateChecker primary = checker((address, agent, deadline) -> {
            calls.incrementAndGet();
            require(address.equals(UpdateChecker.MODRINTH), "Modrinth authoritative");
            return response(versions, false);
        });
        run(primary, "0.6.0");
        require(primary.getAvailableUpdate().getVersion().toString().equals("0.7.0"), "Maximum valid version");
        require(calls.get() == 1, "No unnecessary fallback");
        for (String installed : new String[]{"0.7.0", "0.7.0+build.1", "0.8.0"}) {
            UpdateChecker current = checker((address, agent, deadline) -> response(versions, false));
            run(current, installed);
            require(current.getAvailableUpdate() == null, "No equal/older update");
        }
        UpdateChecker fallback = checker((address, agent, deadline) -> {
            if (address.equals(UpdateChecker.MODRINTH)) {
                throw new IOException("offline");
            }
            if (address.endsWith("page=1")) {
                return response(array(github("0.6.0")), true);
            }
            return response(
                    array(github("v0.7.0-alpha.2"), github("8.0.0").replace("\"draft\":false", "\"draft\":true")),
                    false);
        });
        run(fallback, "0.6.0");
        require(fallback.getAvailableUpdate().getVersion().toString().equals("0.7.0-alpha.2"), "Later-page prerelease");
        require(fallback.getAvailableUpdate().getPageUri().getPath().endsWith("/v0.7.0-alpha.2"), "Original tag link");
        UpdateChecker invalid = checker((address, agent, deadline) -> response("bad json", false));
        run(invalid, "0.6.0");
        require(invalid.getAvailableUpdate() == null, "Bad responses silent");
        UpdateChecker incomplete = checker(
                (address, agent, deadline) -> response("[]", !address.equals(UpdateChecker.MODRINTH)));
        run(incomplete, "0.6.0");
        require(incomplete.getAvailableUpdate() == null, "Incomplete pagination not newest");
        require(!UpdateRelease
                .isReleasePage(URI.create("https://github.com.evil.test/OWeinert/BetaMoon/releases/tag/1.0.0")),
                "Reject lookalike host");
        require(!UpdateRelease.isReleasePage(URI.create("https://github.com/other/repo/releases/tag/1.0.0")),
                "Reject other repo");
    }

    private static void verifyOnce() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        AtomicInteger requests = new AtomicInteger();
        AtomicReference<Thread> worker = new AtomicReference<>();
        UpdateChecker checker = checker((address, agent, deadline) -> {
            worker.set(Thread.currentThread());
            requests.incrementAndGet();
            entered.countDown();
            try {
                require(finish.await(5, TimeUnit.SECONDS), "Worker released");
            } catch (InterruptedException interrupted) {
                throw new IOException(interrupted);
            }
            return response(array(modrinth("0.7.0", "B")), false);
        });
        checker.startOnce("0.6.0");
        require(entered.await(5, TimeUnit.SECONDS), "Startup task starts");
        require(worker.get() != Thread.currentThread(), "HTTP outside client thread");
        checker.startOnce("0.6.0");
        require(checker.getAvailableUpdate() == null, "No premature result");
        finish.countDown();
        worker.get().join(5000);
        require(!worker.get().isAlive(), "Task terminates");
        checker.startOnce("0.6.0");
        require(requests.get() == 1 && checker.getAvailableUpdate() != null, "Result retained without rechecking");
    }

    private static void verifyHttp() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ok", exchange -> {
            byte[] body = "[]".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Link", "<next>; rel=\"next\"");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/rate", exchange -> {
            exchange.sendResponseHeaders(429, -1);
            exchange.close();
        });
        server.createContext("/large", exchange -> {
            exchange.sendResponseHeaders(200, 3 * 1024 * 1024);
            exchange.close();
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        try {
            require(UpdateChecker.readResponse(base + "/ok", "test", deadline()).hasNextPage, "HTTP pagination header");
            for (String path : new String[]{"/rate", "/large"}) {
                try {
                    UpdateChecker.readResponse(base + path, "test", deadline());
                    throw new AssertionError("Expected bounded HTTP failure");
                } catch (IOException expected) {
                    // No response exposed as a valid release list.
                }
            }
            try {
                UpdateChecker.readResponse(base + "/ok", "test", System.nanoTime() - 1);
                throw new AssertionError("Expired deadline accepted");
            } catch (IOException expected) {
                // Entire lookup deadline applies before connecting.
            }
        } finally {
            server.stop(0);
        }
    }

    private static String modrinth(String version, String id) {
        return "{\"version_number\":\"" + version + "\",\"id\":\"" + id + "\",\"status\":\"listed\","
                + "\"version_type\":\"beta\",\"game_versions\":[\"b1.7.3\"],\"loaders\":[\"modloader\"],"
                + "\"files\":[{\"filename\":\"betamoon.jar\",\"url\":\"https://cdn.modrinth.com/file.jar\"}]}";
    }

    private static String github(String tag) {
        String version = tag.startsWith("v") ? tag.substring(1) : tag;
        return "{\"tag_name\":\"" + tag + "\",\"draft\":false,\"prerelease\":true,"
                + "\"published_at\":\"2026-01-01T00:00:00Z\","
                + "\"html_url\":\"https://github.com/OWeinert/BetaMoon/releases/tag/" + tag + "\","
                + "\"assets\":[{\"name\":\"betamoon-" + version + ".jar\","
                + "\"browser_download_url\":\"https://github.com/OWeinert/BetaMoon/releases/download/" + tag
                + "/mod.jar\"}]}";
    }

    private static String array(String... entries) {
        return "[" + String.join(",", entries) + "]";
    }

    private static UpdateChecker checker(UpdateChecker.Transport transport) {
        return new UpdateChecker(transport, Logger.getAnonymousLogger());
    }

    private static UpdateChecker.Response response(String text, boolean next) {
        return new UpdateChecker.Response(text, next);
    }

    private static void run(UpdateChecker checker, String installed) throws Exception {
        Method method = UpdateChecker.class.getDeclaredMethod("checkOnce", String.class);
        method.setAccessible(true);
        method.invoke(checker, installed);
    }

    private static long deadline() {
        return System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    }

    private static int compare(String left, String right) {
        return SemanticVersion.parse(left).comparePrecedence(SemanticVersion.parse(right));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
