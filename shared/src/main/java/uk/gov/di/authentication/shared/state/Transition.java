package uk.gov.di.authentication.shared.state;

public class Transition<T, A, C> {
    private final A action;
    private final T nextState;
    private final Condition<C> condition;

    public Transition(A action, T nextState, Condition<C> condition) {
        this.action = action;
        this.nextState = nextState;
        this.condition = condition;
    }

    public Transition(A action, T nextState) {
        this(action, nextState, new Default<C>());
    }

    public T getNextState() {
        return nextState;
    }

    public A getAction() {
        return action;
    }

    public Condition<C> getCondition() {
        return condition;
    }

    public static <T, A, C> Builder<T, A, C> builder() {
        return new Builder<>();
    }

    public static class Builder<T, A, C> {
        private A action;
        private T nextState;
        private Condition<C> condition;

        public Builder<T, A, C> on(A action) {
            this.action = action;
            return this;
        }

        public Builder<T, A, C> ifCondition(Condition<C> condition) {
            this.condition = condition;
            return this;
        }

        public Builder<T, A, C> then(T nextState) {
            this.nextState = nextState;
            return this;
        }

        public Builder<T, A, C> byDefault() {
            this.condition = new Default<>();
            return this;
        }

        public Transition<T, A, C> build() {
            if (this.condition == null) {
                this.condition = new Default<>();
            }
            return new Transition<>(action, nextState, condition);
        }
    }
}
