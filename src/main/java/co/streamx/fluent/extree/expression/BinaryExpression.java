package co.streamx.fluent.extree.expression;

import lombok.Getter;
import lombok.NonNull;

/**
 * Represents an expression that has a binary operator.
 * 
 * 
 */
@Getter
public final class BinaryExpression extends UnaryExpression {

	private final Expression operator;
	private final Expression second;

	BinaryExpression(int expressionType, Class<?> resultType, Expression operator, Expression first, @NonNull Expression second) {
		super(expressionType, resultType, first);

		if (expressionType == ExpressionType.Conditional)
			if (operator == null)
				throw new IllegalArgumentException(new NullPointerException("operator"));

		this.operator = operator;
		this.second = second;
	}

	@Override
	protected <T> T visit(ExpressionVisitor<T> v) {
		return v.visit(this);
	}

	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		b.append('(');
		if (getOperator() != null) {
			b.append(getOperator().toString());
			b.append(' ');
			b.append(ExpressionType.toString(getExpressionType()));
			b.append(' ');

			b.append(getFirst().toString());
			b.append(' ');

			b.append(':');
		} else {
			b.append(getFirst().toString());
			b.append(' ');
			b.append(ExpressionType.toString(getExpressionType()));
		}
		b.append(' ');
		b.append(getSecond().toString());
		b.append(')');
		return b.toString();
	}
}
