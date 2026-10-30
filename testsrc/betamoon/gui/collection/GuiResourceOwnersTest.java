package betamoon.gui.collection;

import betamoon.gui.interop.GuiResourceOwners;
import betamoon.interop.runtime.NativeContext;
import betamoon.luaapi.utils.LuaCallbackScope;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.luaj.vm2.LuaError;

/**
 * Owner resources are shared by leases, and rejected row candidates cannot
 * retain references or stale caches.
 */
public final class GuiResourceOwnersTest {
    public static void main(String[] arguments) {
        sharingAndRollback();
        generationsAndReentry();
        limitsAndCleanup();
        System.out.println("UI resource owners passed: shared preparation, group rollback, owner generations, "
                + "cache retirement, native/read-only denial, capacity and complete failure cleanup.");
    }

    private static void sharingAndRollback() {
        Map<String, List<Resource>> prepared = new LinkedHashMap<>();
        List<String> retired = new ArrayList<>();
        List<Throwable> failures = new ArrayList<>();
        GuiResourceOwners<Resource> resources = new GuiResourceOwners<>(owner -> {
            if (owner.equals("reject")) {
                throw new IllegalArgumentException("Rejected preparation fixture");
            }
            Resource value = new Resource();
            prepared.computeIfAbsent(owner, key -> new ArrayList<>()).add(value);
            return value;
        }, owner -> 0, retired::add, failures::add);
        GuiResourceOwners<Resource>.Lease root = resources.acquire(Arrays.asList("screen", "theme"));
        Resource screen = root.resource("screen");
        Resource theme = root.resource("theme");
        Map<String, Resource> stable = resources.snapshot();
        expect(() -> resources.acquire(Arrays.asList("screen", "temporary", "reject")));
        require(screen.closes == 0 && theme.closes == 0 && resources.snapshot().equals(stable)
                && prepared.get("temporary").get(0).closes == 1 && retired.equals(Arrays.asList("temporary")),
                "A later rejected owner closed shared resources or retained candidate owners/caches");
        GuiResourceOwners<Resource>.Lease row = resources.acquire(Arrays.asList("screen", "temporary", "screen"));
        require(row.resource("screen") == screen && prepared.get("screen").size() == 1,
                "A row re-prepared shared resources instead of taking one reference per owner");
        expect(() -> row.resource("theme"));
        root.close();
        root.close();
        require(screen.closes == 0 && theme.closes == 1 && row.resource("screen") == screen,
                "Closing static content released resources still used by a row lease");
        row.close();
        require(screen.closes == 1 && prepared.get("temporary").get(1).closes == 1 && resources.snapshot().isEmpty()
                && failures.isEmpty(), "The final row lease retained native resources or their prepared cache");
        expect(() -> row.resource("screen"));
        GuiResourceOwners<Resource>.Lease remaining = resources.acquire(Collections.singleton("screen"));
        Resource last = remaining.resource("screen");
        resources.close();
        remaining.close();
        resources.close();
        require(last.closes == 1 && resources.snapshot().isEmpty(), "Host close double-released remaining leases");
        expect(() -> remaining.resource("screen"));
        expect(() -> resources.acquire(Collections.singleton("screen")));
    }

    private static void generationsAndReentry() {
        Map<String, Long> generations = new LinkedHashMap<>();
        List<Resource> created = new ArrayList<>();
        List<Throwable> failures = new ArrayList<>();
        GuiResourceOwners<Resource> resources = new GuiResourceOwners<>(owner -> {
            Resource value = new Resource();
            created.add(value);
            if (owner.equals("flip")) {
                generations.put("screen", 1L);
            }
            return value;
        }, owner -> generations.getOrDefault(owner, 0L), owner -> {
        }, failures::add);
        expect(() -> resources.acquire(Arrays.asList("screen", "flip")));
        require(resources.snapshot().isEmpty() && created.size() == 2 && created.get(0).closes == 1
                && created.get(1).closes == 1,
                "A later factory invalidated an earlier owner without rejecting the complete lease group");
        GuiResourceOwners<Resource>.Lease old = resources.acquire(Collections.singleton("screen"));
        Resource previous = old.resource("screen");
        generations.put("screen", 2L);
        expect(() -> old.resource("screen"));
        expect(() -> resources.required("screen"));
        int before = created.size();
        expect(() -> resources.acquire(Collections.singleton("screen")));
        require(created.size() == before && previous.closes == 0,
                "Expired references silently reused or replaced another lease's native session");
        old.close();
        GuiResourceOwners<Resource>.Lease current = resources.acquire(Collections.singleton("screen"));
        Resource active = current.resource("screen");
        require(active != previous && previous.closes == 1,
                "Released owner generation did not allow fresh preparation");
        try (NativeContext callback = NativeContext.denied("resource-lease-test.lua")) {
            expect(() -> resources.acquire(Collections.singleton("screen")));
        }
        try (LuaCallbackScope readOnly = LuaCallbackScope.binding()) {
            expect(() -> resources.acquire(Collections.singleton("screen")));
        }
        require(current.resource("screen") == active && active.closes == 0 && created.size() == before + 1,
                "Denied callback preparation changed a still-active owner lease");
        resources.close();

        AtomicReference<GuiResourceOwners<Resource>> holder = new AtomicReference<>();
        GuiResourceOwners<Resource> reentrant = new GuiResourceOwners<>(owner -> {
            if (owner.equals("recursive")) {
                holder.get().close();
            }
            return new Resource();
        }, owner -> 0, owner -> {
        }, failures::add);
        holder.set(reentrant);
        expect(() -> reentrant.acquire(Collections.singleton("recursive")));
        GuiResourceOwners<Resource>.Lease recovered = reentrant.acquire(Collections.singleton("normal"));
        require(recovered.resource("normal").closes == 0 && failures.isEmpty(),
                "Reentrant preparation closed its parent revision or prevented later recovery");
        reentrant.close();
    }

    private static void limitsAndCleanup() {
        for (boolean fatal : new boolean[]{false, true}) {
            List<Resource> created = new ArrayList<>();
            List<Throwable> failures = new ArrayList<>();
            List<String> retired = new ArrayList<>();
            GuiResourceOwners<Resource> resources = new GuiResourceOwners<>(owner -> {
                Resource value = new Resource();
                created.add(value);
                return value;
            }, owner -> 0, owner -> {
                retired.add(owner);
                if (owner.equals("owner0")) {
                    throw new IllegalStateException("Cache retirement fixture");
                }
            }, failures::add);
            List<String> owners = new ArrayList<>();
            for (int index = 0; index < 32; index++) {
                owners.add("owner" + index);
            }
            GuiResourceOwners<Resource>.Lease all = resources.acquire(owners);
            expect(() -> resources.acquire(Collections.singleton("extra")));
            require(created.size() == 32, "Owner capacity rejection acquired more native resources");
            created.get(1).failure = fatal ? new ThreadDeath() : new AssertionError("Resource cleanup fixture");
            boolean rethrown = false;
            try {
                resources.close();
            } catch (ThreadDeath expected) {
                rethrown = true;
            }
            for (Resource resource : created) {
                require(resource.closes == 1, "An earlier resource/cache failure skipped a later owner's cleanup");
            }
            require(retired.size() == 32 && resources.snapshot().isEmpty() && rethrown == fatal
                    && failures.size() == (fatal ? 0 : 1), "Cleanup swallowed a fatal error or retained owner caches");
            all.close();
            resources.close();
        }
    }

    private static final class Resource implements AutoCloseable {
        int closes;
        Error failure;
        public void close() {
            closes++;
            if (failure != null) {
                throw failure;
            }
        }
    }

    private static void expect(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException | IllegalStateException | LuaError expected) {
            return;
        }
        throw new AssertionError("Invalid UI resource lease was accepted");
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
