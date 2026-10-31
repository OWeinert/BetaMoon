package betamoon.gui.collection;

import betamoon.gui.component.GuiComponentPlan;
import betamoon.gui.component.GuiComponentRegistry;
import betamoon.gui.component.GuiComponentRowFactory;
import betamoon.gui.component.GuiComponentStateStore;
import betamoon.gui.framework.GuiContainer;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiPreparationPhase;
import betamoon.gui.framework.GuiScene;
import betamoon.gui.framework.HeadlessGuiFrames;
import betamoon.gui.interop.GuiResourceOwners;
import betamoon.gui.interop.GuiPreparedRowEnvironment;
import betamoon.gui.drawing.GuiPaintBudget;
import betamoon.gui.style.GuiThemeRegistry;
import betamoon.gui.style.GuiDrawable;
import betamoon.gui.style.GuiFontFace;
import betamoon.luaapi.client.ui.GuiStyleParser;
import betamoon.gui.presentation.GuiDocumentDefinition;
import betamoon.gui.presentation.GuiNodeDefinition;
import betamoon.gui.presentation.GuiPresentationHost;
import betamoon.gui.presentation.GuiPresentationSources;
import betamoon.gui.screen.GuiActionDefinition;
import betamoon.gui.screen.GuiActionDefinition.Scope;
import betamoon.gui.screen.GuiActionQueue;
import betamoon.gui.screen.GuiActionTicket;
import betamoon.gui.screen.GuiActionTicket.Status;
import betamoon.gui.component.GuiComponentSchema;
import betamoon.gui.widget.GuiStyledView;
import betamoon.luaapi.client.ui.GuiComponentParser;
import betamoon.luaapi.client.ui.GuiPaintCompiler;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.ScriptResourceTracker;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.lib.ZeroArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.lwjgl.input.Keyboard;

/**
 * Actual Lua factories, typed component state, real shared input, dormant
 * restoration and lease rollback.
 */
public final class GuiComponentRowsTest {
    private static final String OWNER = "component-row-screen.lua";
    private static final String COMPONENT = "component-row-counter.lua";
    private static final String THEME = "component-row-theme.lua";

    public static void main(String[] arguments) {
        Fixture fixture = new Fixture();
        try {
            fixture.run();
        } finally {
            fixture.close();
        }
        fatalPreparationCleanup();
        System.out.println("Component rows passed: scoped Lua realization, shared controls, keyed typed/editor state, "
                + "atomic candidate/lease rollback, dependency refresh, bounded idle and disposal.");
    }

    private static final class Fixture implements AutoCloseable {
        final Globals lua = JsePlatform.standardGlobals();
        final GuiComponentRegistry components = new GuiComponentRegistry();
        final GuiRowBudget budget = new GuiRowBudget();
        final Host host = new Host();
        final GuiThemeRegistry themes = new GuiThemeRegistry();
        final GuiPaintBudget paint = new GuiPaintBudget();
        final List<Throwable> failures = new ArrayList<>();
        final GuiResourceOwners<Resource> resources = new GuiResourceOwners<>(owner -> new Resource(),
                ScriptResourceTracker::ownerGeneration, owner -> {
                }, failures::add);
        final GuiComponentPlan.Services services = new GuiComponentPlan.Services() {
            public GuiPaintCompiler.Resources paintResources(String owner) {
                throw new IllegalArgumentException("No custom graphics in this fixture");
            }

            public void requireHost() {
            }

            public void failure(String owner, String path, Throwable failure) {
                require(!path.equals("root"), "A row callback failure escaped as an owning screen root failure");
                require(path.contains("/row/"), "A row callback/template failure lost its row diagnostic identity");
                failures.add(failure);
            }

            public Set<String> events() {
                return new HashSet<>(Arrays.asList("control", "open", "update", "reload"));
            }

            public LuaValue event(LuaTable context, LuaTable event) {
                lifecycleCalls++;
                return LuaValue.NIL;
            }
        };
        final GuiVirtualCollection collection;
        final GuiScene scene = new GuiScene();
        final List<GuiCollectionModel.Record> records = new ArrayList<>();
        final Actions actions = new Actions();
        int liveEnvironments;
        int lifecycleCalls;

        Fixture() {
            lua.set("submit", actions.submit);
            lua.set("readOnlyCheck", new ZeroArgFunction() {
                public LuaValue call() {
                    require(!GuiPreparationPhase.active(), "A Lua factory inherited Java preparation authority");
                    try {
                        LuaCallbackScope.requireUiStateWrite();
                        throw new AssertionError("A Lua factory could mutate UI state");
                    } catch (LuaError expected) {
                        return NIL;
                    }
                }
            });
            registerCounter("Count ");
            registerTheme("#FFFFFF");
            LuaValue builder = lua.load("calls=0; failKey=''; return function(row) calls=calls+1; readOnlyCheck(); "
                    + "assert(not pcall(function() row.label='mutated' end)); "
                    + "if row.key==failKey then error('later row fixture') end; "
                    + "return {type='column',gap=2,children={"
                    + "{component='test:row_counter',id='counter',props={label=row.label}},"
                    + "{type='textbox',id='editor',height=24,maximumLength=65536,on={change=function(ctx,event) "
                    + "submit(); ctx.state:set('mirror',event.value); "
                    + "if failEvent then error('row event fixture') end end}}}} end").call();
            GuiComponentRowFactory factory = new GuiComponentRowFactory(components, OWNER, "test:rows", builder,
                    GuiComponentParser.schema(lua.load("return {mirror={type='string',default=''}}").call(),
                            "row.state", false),
                    Collections.emptySet(), this::environment, text -> text.length() * 6, () -> 0);
            collection = new GuiVirtualCollection(budget, factory, failures::add);
            collection.setMetrics(80, 1);
            for (int index = 0; index < 4096; index++) {
                records.add(new GuiCollectionModel.Record("row" + index, null, "row" + index, true,
                        Collections.singletonMap("index", index)));
            }
            collection.setModel(GuiCollectionModel.prepare(records));
            scene.updateEnvironment(null, null, 320, 160, 320, 160);
            scene.setContent(collection);
        }

        void registerCounter(String prefix) {
            LuaTable dependencies = new LuaTable();
            for (int index = 0; index < 70; index++) {
                String key = "test:row_support_" + index;
                dependencies.set(index + 1, LuaValue.valueOf(key));
                components.add(GuiComponentParser.parse(COMPONENT,
                        lua.load("return {key='" + key + "',build=function() return {type='text',text='Support'} end}")
                                .call()));
            }
            lua.set("supportDependencies", dependencies);
            components.add(GuiComponentParser.parse(COMPONENT,
                    lua.load("return {key='test:row_counter',dependencies=supportDependencies,"
                            + "props={label={type='string',default='Row'}},"
                            + "state={count={type='integer',default=0}},build=function(props) "
                            + "return {type='button',id='count',height=20,bind=function(v) "
                            + "bindingCalls=(bindingCalls or 0)+1; return {text='" + prefix
                            + "'..props.label..':'..v.state:get('count')} end,"
                            + "on={activate=function(ctx) submit(); ctx.state:set('count',ctx.state:get('count')+1) end}} end}")
                            .call()));
        }

        void registerTheme(String tint) {
            themes.add(GuiStyleParser.theme(THEME,
                    lua.load("return {key='test:row_theme',base='minimal',templates={button={parts={'content','focus'},"
                            + "paint=function(view) templateCalls=(templateCalls or 0)+1; local result={}; "
                            + "for i=1,1024 do result[i]={op='rect',width=1,height=1,color='" + tint
                            + "'} end; return result end}}}").call()));
        }

        GuiComponentRowFactory.Environment environment(GuiRowFactory.Context context) {
            GuiPreparedRowEnvironment<Resource> environment = new GuiPreparedRowEnvironment<>(host, services, resources,
                    paint.scope(), "test:row_theme", themes, "test:rows/row/" + context.record.key, () -> true);
            liveEnvironments++;
            return new GuiComponentRowFactory.Environment() {
                boolean closed;

                public GuiPresentationHost host() {
                    return environment.host();
                }

                public GuiComponentPlan.Services services() {
                    return environment.services();
                }

                public void prepare(GuiComponentPlan plan) {
                    environment.prepare(plan);
                }

                public void validate() {
                    environment.validate();
                }

                public boolean needsRefresh() {
                    return environment.needsRefresh();
                }

                public Object refreshStamp() {
                    return environment.refreshStamp();
                }

                public GuiPaintBudget paintBudget() {
                    return environment.paintBudget();
                }

                public void close() {
                    if (!closed) {
                        closed = true;
                        liveEnvironments--;
                        environment.close();
                    }
                }
            };
        }

        void run() {
            prepare();
            require(collection.realizedRows() == 3 && lua.get("calls").checkint() == 3 && liveEnvironments == 3
                    && resources.snapshot().size() == 3 && failures.isEmpty(),
                    "4096 Lua records did not create only the bounded accepted shared graphs/owner groups: "
                            + failures);
            GuiElement first = collection.rowElement("row0");
            GuiStyledView button = control(first, "button");
            click(button);
            actions.queue.dispatch(Scope.WORLD);
            prepare();
            require(button.text().equals("Count row0:1"),
                    "Realized component input did not update isolated typed state");
            GuiStyledView editor = control(first, "textbox");
            scene.getContext().requestFocus(editor);
            for (char letter : "saved draft".toCharArray()) {
                scene.keyTyped(letter, Keyboard.KEY_A, false);
                actions.queue.dispatch(Scope.WORLD);
            }
            editor.editor().move(1, false);
            editor.editor().move(5, true);
            prepare();
            Map<String, Object> accepted = collection.rowState("row0");
            require(state(accepted).get(GuiComponentStateStore.source(GuiComponentPlan.SCREEN_STATE, "mirror"))
                    .equals("saved draft"), "Row root typed state did not follow its actual editor event");
            int calls = lua.get("calls").checkint();
            int bindingCalls = lua.get("bindingCalls").checkint();
            int templateCalls = lua.get("templateCalls").checkint();
            int observations = host.observers.size();
            for (int frame = 0; frame < 10; frame++) {
                prepare();
                collection.sampleRows();
            }
            require(lua.get("calls").checkint() == calls && lua.get("bindingCalls").checkint() == bindingCalls
                    && lua.get("templateCalls").checkint() == templateCalls && lifecycleCalls == 0
                    && host.observers.size() == observations,
                    "An unchanged Lua row reran its factory or accumulated subscriptions");

            GuiStyledView limited = control(collection.rowElement("row1"), "textbox");
            scene.getContext().requestFocus(limited);
            int edits = 0;
            GuiActionTicket beforeEdit = actions.last;
            while (!limited.interaction("invalid") && edits < 12000) {
                beforeEdit = actions.last;
                scene.keyTyped('x', Keyboard.KEY_X, false);
                actions.queue.dispatch(Scope.WORLD);
                edits++;
            }
            require(edits > 1000 && edits < 12000
                    && (actions.last == beforeEdit || actions.last.status() == Status.CANCELLED) && failures.isEmpty(),
                    "Connected typed/editor admission failed to refuse oversized row state and its queued intent: "
                            + edits + "/" + actions.last.status() + "/" + failures);
            scene.keyTyped('\0', Keyboard.KEY_BACK, false);
            actions.queue.dispatch(Scope.WORLD);
            require(!limited.interaction("invalid") && actions.last.status() == Status.SUCCEEDED,
                    "A valid row edit did not recover after combined state admission refusal");

            lua.set("failKey", LuaValue.valueOf("row1"));
            collection.refreshRows();
            prepare();
            require(failures.size() == 1 && collection.rowElement("row0") == first && liveEnvironments == 3
                    && host.observers.size() == observations && budget.liveRows() == 3,
                    "A later Lua row error partly retired accepted views, state, leases or subscriptions");
            int refused = lua.get("calls").checkint();
            prepare();
            require(lua.get("calls").checkint() == refused, "An unchanged failed Lua candidate retried on idle");
            lua.set("failKey", LuaValue.valueOf(""));
            collection.refreshRows();
            prepare();
            first = collection.rowElement("row0");
            require(first != button && control(first, "button").text().equals("Count row0:1")
                    && control(first, "textbox").text().equals("saved draft"),
                    "Prepared Lua row refresh lost compatible typed/control state");
            collection.setScrollOffset(800);
            prepare();
            require(collection.rowElement("row0") == null && liveEnvironments == collection.realizedRows(),
                    "Offscreen Lua rows retained view/resource environments");
            collection.setScrollOffset(0);
            prepare();
            GuiStyledView restored = control(collection.rowElement("row0"), "textbox");
            require(restored != editor && restored.text().equals("saved draft")
                    && restored.editor().selectionStart() == 1 && restored.editor().selectionEnd() == 5
                    && control(collection.rowElement("row0"), "button").text().equals("Count row0:1"),
                    "Dormant Lua rows lost component state, editor drafts or selection");

            GuiElement beforeTheme = collection.rowElement("row0");
            registerTheme("#55AAFF");
            prepare();
            require(collection.rowElement("row0") != beforeTheme
                    && control(collection.rowElement("row0"), "button").text().equals("Count row0:1")
                    && liveEnvironments == 3 && resources.snapshot().size() == 3,
                    "A dynamic row theme replacement lost typed state or retained old candidate environments");

            GuiElement beforeBadTheme = collection.rowElement("row0");
            int beforeThemeFailure = failures.size();
            themes.add(GuiStyleParser.theme(THEME,
                    lua.load("return {key='test:row_theme',base='minimal',templates={button={parts={'content','focus'},"
                            + "paint=function() error('template fixture') end}}}").call()));
            prepare();
            require(collection.rowElement("row0") == beforeBadTheme && failures.size() > beforeThemeFailure
                    && liveEnvironments == 3 && resources.snapshot().size() == 3,
                    "A failed dynamic row template retired accepted visuals, references or resources");
            int badThemeCalls = lua.get("calls").checkint();
            prepare();
            require(lua.get("calls").checkint() == badThemeCalls, "An unchanged invalid row theme retried Lua on idle");
            registerTheme("#55AAFF");
            prepare();
            require(collection.rowElement("row0") != beforeBadTheme,
                    "Replacing an invalid row theme did not retry its rejected candidate");

            GuiElement acceptedViewport = collection.rowElement("row0");
            int beforePaintRefusal = failures.size();
            collection.setMetrics(40, 1);
            prepare();
            require(collection.rowElement("row0") == acceptedViewport && collection.realizedRows() == 3
                    && failures.size() == beforePaintRefusal + 1 && liveEnvironments == 3
                    && resources.snapshot().size() == 3,
                    "An over-budget complete row window partly retired accepted templates, leases or viewport");
            collection.setMetrics(80, 1);
            prepare();

            click(control(collection.rowElement("row0"), "button"));
            registerCounter("Changed ");
            prepare();
            actions.queue.dispatch(Scope.CLIENT);
            require(actions.last.status() == Status.CANCELLED,
                    "Accepted row callback replacement left an earlier Lua request eligible");
            require(control(collection.rowElement("row0"), "button").text().equals("Changed row0:2")
                    && host.observers.size() == observations && liveEnvironments == 3,
                    "Accepted component registration refresh lost keyed state or leaked old resources");
            ScriptResourceTracker.unload(COMPONENT);
            require(resources.snapshot().size() == 0 && liveEnvironments == 0,
                    "Expired component dependencies retained their row resource leases");
            prepare();
            registerCounter("Recovered ");
            collection.refreshRows();
            prepare();
            require(control(collection.rowElement("row0"), "button").text().equals("Recovered row0:2")
                    && liveEnvironments == 3 && resources.snapshot().size() == 3,
                    "A reloaded row dependency could not recover from its last admitted detached state");
            click(control(collection.rowElement("row0"), "button"));
            ScriptResourceTracker.unload(THEME);
            actions.queue.dispatch(Scope.CLIENT);
            require(actions.last.status() == Status.CANCELLED && liveEnvironments == 0
                    && resources.snapshot().isEmpty(),
                    "An unloaded dynamic theme owner retained row resources or eligible callback intent");
            registerTheme("#FFFFFF");
            collection.refreshRows();
            prepare();
            require(control(collection.rowElement("row0"), "button").text().equals("Recovered row0:3")
                    && liveEnvironments == 3, "A reloaded theme did not restore admitted keyed row state");
            int beforeFailure = failures.size();
            lua.set("failEvent", LuaValue.TRUE);
            scene.getContext().requestFocus(control(collection.rowElement("row0"), "textbox"));
            scene.keyTyped('x', Keyboard.KEY_X, false);
            actions.queue.dispatch(Scope.WORLD);
            require(failures.size() == beforeFailure + 1 && actions.last.status() == Status.CANCELLED
                    && control(collection.rowElement("row1"), "button").isEnabled(),
                    "A failed row-root callback retained intent or disabled an unrelated row");
            collection.close();
            require(host.observers.isEmpty() && liveEnvironments == 0 && resources.snapshot().isEmpty()
                    && budget.reservedElements() == 0,
                    "Closing Lua rows leaked sources, leases or element reservations");
        }

        void prepare() {
            scene.prepare();
            HeadlessGuiFrames.layout(scene);
        }

        void click(GuiElement element) {
            int x = element.getLeft() + 3;
            int y = element.getTop() + 3;
            scene.mousePressed(x, y, 0);
            scene.mouseReleased(x, y, 0);
        }

        public void close() {
            collection.close();
            resources.close();
            paint.close();
            budget.close();
            ScriptResourceTracker.unload(COMPONENT);
            ScriptResourceTracker.unload(OWNER);
            ScriptResourceTracker.unload(THEME);
        }
    }

    private static void fatalPreparationCleanup() {
        Host host = new Host();
        boolean[] closed = {false};
        GuiRowBudget budget = new GuiRowBudget();
        GuiComponentRowFactory factory = new GuiComponentRowFactory(new GuiComponentRegistry(), OWNER, "test:fatal",
                new ZeroArgFunction() {
                    public LuaValue call() {
                        throw new ThreadDeath();
                    }
                }, GuiComponentSchema.EMPTY, Collections.emptySet(),
                context -> new GuiComponentRowFactory.Environment() {
                    public GuiPresentationHost host() {
                        return host;
                    }

                    public GuiComponentPlan.Services services() {
                        throw new AssertionError("A failing factory must not start component compilation");
                    }

                    public void close() {
                        closed[0] = true;
                        throw new IllegalArgumentException("cleanup fixture");
                    }
                }, text -> text.length() * 6, () -> 0);
        try (GuiPreparationPhase preparation = GuiPreparationPhase.open()) {
            factory.prepare(new GuiRowFactory.Context(new GuiScene().getContext(), budget,
                    new GuiCollectionModel.Record("row", null, "Row", true, Collections.emptyMap()), 0, 320, 80,
                    Collections.emptyMap(), null));
            throw new AssertionError("A fatal Lua factory error was swallowed");
        } catch (ThreadDeath expected) {
            require(closed[0] && expected.getSuppressed().length == 1 && budget.reservedElements() == 0
                    && host.observers.isEmpty(), "Cleanup replaced a fatal row factory error or leaked resources");
        } finally {
            budget.close();
        }
    }

    private static final class Actions implements GuiActionQueue.Activation {
        final GuiActionQueue queue = new GuiActionQueue(() -> 0);
        final GuiActionDefinition action = new GuiActionDefinition("apply", Scope.WORLD, GuiComponentSchema.EMPTY,
                new ZeroArgFunction() {
                    public LuaValue call() {
                        return NIL;
                    }
                });
        GuiActionTicket last;
        final LuaValue submit = new ZeroArgFunction() {
            public LuaValue call() {
                GuiActionQueue.Submission submission = queue.submit(Actions.this, action, Collections.emptyMap());
                require(submission.ticket != null, "Accepted row callback could not submit: " + submission.reason);
                last = submission.ticket;
                return NIL;
            }
        };

        public String owner() {
            return OWNER;
        }

        public long revision() {
            return 0;
        }

        public boolean active() {
            return true;
        }

        public String unavailable(Scope scope) {
            return null;
        }

        public Object execute(GuiActionDefinition definition, Map<String, Object> payload) {
            return null;
        }

        public void failed(String name, Throwable failure) {
            throw new AssertionError(failure);
        }
    }

    private static final class Host implements GuiPresentationHost {
        final List<Observer> observers = new ArrayList<>();

        public GuiPresentationSources snapshot() {
            return new GuiPresentationSources(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
        }

        public Map<String, Object> baseline(GuiNodeDefinition node, GuiPresentationSources sources) {
            return Collections.emptyMap();
        }

        public List<AutoCloseable> observe(Observer observer) {
            observers.add(observer);
            return Collections.singletonList(() -> observers.remove(observer));
        }

        public LuaTable context(LuaCallbackScope scope) {
            return new LuaTable();
        }

        public GuiPresentationHost forDefinition(GuiDocumentDefinition definition) {
            return this;
        }
    }

    private static final class Resource implements GuiPreparedRowEnvironment.Resources {
        public GuiPaintCompiler.Resources painting() {
            return new GuiPaintCompiler.Resources() {
                public GuiFontFace font(String key) {
                    return GuiFontFace.NATIVE;
                }

                public GuiDrawable image(String key) {
                    return GuiDrawable.solid(0);
                }
            };
        }

        public void bindContext(LuaCallbackScope scope) {
            scope.requireActive();
        }

        public void close() {
        }
    }

    private static GuiStyledView control(GuiElement element, String kind) {
        if (element instanceof GuiStyledView && ((GuiStyledView) element).kind().equals(kind)) {
            return (GuiStyledView) element;
        }
        if (element instanceof GuiContainer) {
            for (GuiElement child : ((GuiContainer) element).getChildren()) {
                GuiStyledView found = control(child, kind);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> state(Map<String, Object> snapshot) {
        return (Map<String, Object>) snapshot.get("state");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
