package co.streamx.fluent.extree.expression;

import java.util.List;

import lombok.Getter;
import lombok.NonNull;

/**
 * Describes a lambda signature and an {@link Expression} delegate that returns {@link InvocableExpression}. The
 * delegate may encapsulate a parameter or {@link InvocationExpression}.
 * 
 * 
 */

@Getter
public final class DelegateExpression extends InvocableExpression {

	private final Expression delegate;

	DelegateExpression(Class<?> resultType, @NonNull Expression delegate, List<ParameterExpression> params) {
		super(ExpressionType.Delegate, resultType, params);

		if (!InvocableExpression.class.isAssignableFrom(delegate.getResultType()))
			throw new IllegalArgumentException("delegate");

		this.delegate = delegate;
	}

	@Override
	protected <T> T visit(ExpressionVisitor<T> v) {
		return v.visit(this);
	}

	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		// b.append('<');
		// List<ParameterExpression> arguments = getParameters();
		// if (arguments.size() > 0) {
		// b.append('(');
		// for (int i = 0; i < arguments.size(); i++) {
		// if (i > 0) {
		// b.append(',');
		// b.append(' ');
		// }
		// ParameterExpression pe = arguments.get(i);
		// b.append(pe.getResultType().getName());
		// b.append(' ');
		// b.append(pe.toString());
		// }
		// b.append(')');
		// }
		// b.append(" -> ");
		// b.append(getResultType().getName());
		// b.append('>');
		b.append('{');
		b.append(getDelegate());
		b.append('}');
		return b.toString();
	}
}
