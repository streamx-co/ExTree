package co.streamx.fluent.extree.expression;

import java.util.List;

import lombok.Getter;
import lombok.NonNull;

/**
 * Provides the base class from which the expression that represent invocable operations are derived.
 * 
 * 
 */

@Getter
public abstract class InvocableExpression extends Expression {

	private final List<ParameterExpression> parameters;

	protected InvocableExpression(int expressionType, Class<?> resultType, @NonNull List<ParameterExpression> params) {
		super(expressionType, resultType);

		this.parameters = params;
	}
}
