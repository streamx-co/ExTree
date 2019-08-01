package co.streamx.fluent.extree.expression;

import lombok.Getter;

/**
 * Represents an expression that has a constant value.
 * 
 * 
 */
@Getter
public final class ConstantExpression extends Expression {

    private final Object value;

    ConstantExpression(Class<?> resultType, Object value) {
        super(ExpressionType.Constant, resultType);

        this.value = value;
    }

    @Override
    protected <T> T visit(ExpressionVisitor<T> v) {
        return v.visit(this);
    }

    @Override
    public String toString() {
        Object value = getValue();
        return String.valueOf(value);
    }
}
