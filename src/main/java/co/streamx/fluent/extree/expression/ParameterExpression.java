package co.streamx.fluent.extree.expression;

import lombok.Getter;

/**
 * Represents an indexed parameter expression.
 * 
 * 
 */

@Getter
public final class ParameterExpression extends Expression {

	private final int index;

	ParameterExpression(Class<?> resultType, int index) {
		super(ExpressionType.Parameter, resultType);

		if (index < 0)
			throw new IndexOutOfBoundsException("index");

		this.index = index;
	}

	@Override
	protected <T> T visit(ExpressionVisitor<T> v) {
		return v.visit(this);
	}

	@Override
	public String toString() {
		return "P" + getIndex();
	}
}
