package co.streamx.fluent.extree;

import java.io.Serializable;
import java.util.function.Function;

import co.streamx.fluent.extree.expression.Expression;
import co.streamx.fluent.extree.expression.InvocationExpression;
import co.streamx.fluent.extree.expression.LambdaExpression;
import co.streamx.fluent.extree.expression.MemberExpression;
import co.streamx.fluent.extree.expression.UnaryExpression;

public class Fluent<T> {

	public static interface Property<T, R> extends Function<T, R>, Serializable {

	}

	private LambdaExpression<Function<T, ?>> parsed;
	private String member;

	public Fluent<T> property(Property<T, ?> propertyRef) {
		LambdaExpression<Function<T, ?>> parsed = LambdaExpression
				.parse(propertyRef);
		Expression body = parsed.getBody();
		Expression method = body;
		while (method instanceof UnaryExpression)
			method = ((UnaryExpression) method).getFirst();

		member = ((MemberExpression) ((InvocationExpression) method)
				.getTarget()).getMember().toString();
		this.parsed = parsed;
		return this;
	}

	public LambdaExpression<Function<T, ?>> getParsed() {
		return parsed;
	}

	public String getMember() {
		return member;
	}
}
