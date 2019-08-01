package co.streamx.fluent.extree.expression;

import lombok.Getter;
import lombok.NonNull;

/**
 * Represents an expression that has a unary operator.
 * 
 * 
 */

@Getter
public class UnaryExpression extends Expression {

	private final Expression first;

	UnaryExpression(int expressionType, Class<?> resultType, @NonNull Expression operand) {
		super(expressionType, resultType);

		this.first = operand;
	}

	@Override
	protected <T> T visit(ExpressionVisitor<T> v) {
		return v.visit(this);
	}

	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();

		if (getExpressionType() == ExpressionType.Convert) {
			b.append('(');
			b.append(getResultType().getName());
			b.append(')');
		} else
			b.append(ExpressionType.toString(getExpressionType()));
		b.append(getFirst().toString());

		return b.toString();
	}
}
