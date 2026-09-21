package betamoon.entity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.src.Entity;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.NBTTagList;

/** Saved state-machine selection and simulation-time timers. */
public final class EntityBehaviorState {
    private final Map<String, Timer> timers = new LinkedHashMap<>();
    private String state = "";
    private boolean transitioning;

    public String state(EntityBehaviorDefinition definition) {
        bind(definition);
        return state.isEmpty() ? null : state;
    }

    public boolean setState(Entity entity, EntityTypeDefinition type, String next) {
        EntityBehaviorDefinition definition = type.behavior;
        bind(definition);
        if (!definition.states.contains(next)) {
            throw new IllegalArgumentException("Unknown entity behavior state: " + next);
        }
        if (next.equals(state)) {
            return false;
        }
        if (transitioning) {
            throw new IllegalStateException("An entity behavior state transition is already in progress");
        }
        transitioning = true;
        try {
            String previous = state;
            EntityBehaviorEvents.stateExit(entity, type, previous, next);
            if (entity.isDead) {
                return false;
            }
            state = next;
            EntityBehaviorEvents.stateEnter(entity, type, previous, next);
            return true;
        } finally {
            transitioning = false;
        }
    }

    public void start(String name, int ticks, int repeat) {
        if (!timers.containsKey(name) && timers.size() >= 32) {
            throw new IllegalStateException("An entity may have at most 32 active timers");
        }
        timers.put(name, new Timer(ticks, repeat));
    }

    public boolean cancel(String name) {
        return timers.remove(name) != null;
    }

    public int remaining(String name) {
        Timer timer = timers.get(name);
        return timer == null ? -1 : timer.remaining;
    }

    public void tick(Entity entity, EntityTypeDefinition type) {
        if (type.behavior == null || entity.isDead || entity.worldObj == null || entity.worldObj.multiplayerWorld) {
            return;
        }
        bind(type.behavior);
        List<String> due = new ArrayList<>();
        for (Map.Entry<String, Timer> entry : timers.entrySet()) {
            if (--entry.getValue().remaining <= 0) {
                due.add(entry.getKey());
            }
        }
        for (String name : due) {
            Timer timer = timers.get(name);
            if (timer == null || timer.remaining > 0) {
                continue;
            }
            int repeat = timer.repeat;
            if (repeat > 0) {
                timer.remaining = repeat;
            } else {
                timers.remove(name);
            }
            EntityBehaviorEvents.timer(entity, type, name, repeat > 0);
            if (entity.isDead) {
                return;
            }
        }
    }

    private void bind(EntityBehaviorDefinition definition) {
        if (definition == null) {
            state = "";
            timers.clear();
            return;
        }
        if (definition.onTimer.isnil()) {
            timers.clear();
        }
        if (!state.isEmpty() && !definition.states.contains(state)) {
            state = "";
        }
        if (state.isEmpty()) {
            state = definition.initialState;
        }
    }

    public void write(NBTTagCompound tag) {
        if (!state.isEmpty()) {
            tag.setString("BetaMoonBehaviorState", state);
        }
        NBTTagList savedTimers = new NBTTagList();
        for (Map.Entry<String, Timer> entry : timers.entrySet()) {
            NBTTagCompound saved = new NBTTagCompound();
            saved.setString("Name", entry.getKey());
            saved.setInteger("Remaining", entry.getValue().remaining);
            saved.setInteger("Repeat", entry.getValue().repeat);
            savedTimers.setTag(saved);
        }
        tag.setTag("BetaMoonTimers", savedTimers);
    }

    public void read(NBTTagCompound tag) {
        state = tag.getString("BetaMoonBehaviorState");
        timers.clear();
        NBTTagList savedTimers = tag.getTagList("BetaMoonTimers");
        for (int index = 0; index < savedTimers.tagCount() && timers.size() < 32; index++) {
            NBTTagCompound saved = (NBTTagCompound) savedTimers.tagAt(index);
            String name = saved.getString("Name");
            int remaining = saved.getInteger("Remaining");
            int repeat = saved.getInteger("Repeat");
            if (name.matches("[a-z][a-z0-9_]{0,63}") && remaining > 0 && remaining <= 120000
                    && repeat >= 0 && repeat <= 120000) {
                timers.put(name, new Timer(remaining, repeat));
            }
        }
    }

    private static final class Timer {
        private int remaining;
        private final int repeat;

        private Timer(int remaining, int repeat) {
            this.remaining = remaining;
            this.repeat = repeat;
        }
    }
}
