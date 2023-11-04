package co.streamx.fluent.extree.expression;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;

/**
 * Represents an expression that applies a delegate or lambda expression to a list of argument expressions.
 * 
 * 
 */

@Getter
public final class InvocationExpression extends Expression {

	private final InvocableExpression target;
	private final List<Expression> arguments;

	InvocationExpression(InvocableExpression method, List<? extends Expression> arguments) {
		super(ExpressionType.Invoke, method.getResultType());

		List<ParameterExpression> pp = method.getParameters();

		for (int i = 0; i < pp.size(); i++) {
			Class<?> resultType = arguments.get(i).getResultType();
			if (resultType == Object.class)
				continue; // if there is accessor method, the cast might be there
			Class<?> paramType = pp.get(i).getResultType();
			if (!TypeConverter.isAssignable(paramType, resultType)) {
				if (paramType.isInterface() && resultType == LambdaExpression.class)
					continue; // special case
				throw new IllegalArgumentException(String.valueOf(i));
			}
		}

		this.target = method;
		this.arguments = new ArrayList<>(arguments);
	}

	@Override
	protected <T> T visit(ExpressionVisitor<T> v) {
		return v.visit(this);
	}

	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		InvocableExpression normalized = getTarget();
		b.append(normalized.toString());
		if (normalized.getExpressionType() != ExpressionType.FieldAccess) {
			b.append('(');
			List<ParameterExpression> parameters = normalized.getParameters();
			for (int i = 0; i < parameters.size(); i++) {
				if (i > 0) {
					b.append(',');
					b.append(' ');
				}
				b.append(arguments.get(parameters.get(i).getIndex()).toString());
			}
			b.append(')');
		}
		return b.toString();
	}
}
