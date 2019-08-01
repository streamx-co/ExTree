package co.streamx.fluent.extree.expression;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.NonNull;

/**
 * Describes a one-dimensional array and initializing it from a list of elements.
 * 
 * 
 */

@Getter
public final class NewArrayInitExpression extends Expression {

    private final Class<?> componentType;
    private final List<Expression> initializers;

    NewArrayInitExpression(@NonNull Class<?> type, int count) {
        this(type, Arrays.asList(new Expression[count]));
    }

    NewArrayInitExpression(@NonNull Class<?> type, @NonNull List<Expression> initializers) {
        super(ExpressionType.NewArrayInit, Array.newInstance(type, 0).getClass());
        this.componentType = type;
        this.initializers = initializers;
    }

    public int getCount() {
        return initializers.size();
    }

    @Override
    protected <T> T visit(ExpressionVisitor<T> v) {
        return v.visit(this);
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append('{');
        b.append(initializers.stream().map(Expression::toString).collect(Collectors.joining(",")));
        b.append('}');
        return b.toString();
    }
}
