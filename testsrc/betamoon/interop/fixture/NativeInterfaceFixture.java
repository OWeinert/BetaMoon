package betamoon.interop.fixture;

import java.util.ArrayList;
import java.util.List;

/**
 * An ordinary unrenamed mod API with no BetaMoon-specific registration hooks.
 */
public final class NativeInterfaceFixture {
    public interface Values {
        int measure(int value);

        long measure(long value);

        Object echo(Object value);

        default int offset(int value) {
            return value + 1;
        }
    }

    public interface Alias {
        int measure(int value);
    }

    public interface Marker {
    }

    public interface Incompatible {
        String measure(int value);
    }

    public interface Observer {
        void changed(int value);
    }

    private interface Hidden {
        int hidden();
    }

    public static final Consumer HOST = new Consumer();

    public static final class Consumer {
        private final List<Observer> observers = new ArrayList<>();

        public void add(Observer observer) {
            observers.add(observer);
        }

        public void addFail(Observer observer) {
            add(observer);
            throw new IllegalStateException("fixture partial registration");
        }

        public void remove(Observer observer) {
            observers.removeIf(value -> value == observer);
        }

        public void removeFail(Observer observer) {
            remove(observer);
            throw new IllegalStateException("fixture failed removal");
        }

        public void emit(int value) {
            for (int i = 0; i < observers.size(); i++) {
                observers.get(i).changed(value);
            }
        }

        public int size() {
            return observers.size();
        }
    }
}
