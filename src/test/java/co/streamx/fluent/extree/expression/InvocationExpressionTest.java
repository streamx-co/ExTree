package co.streamx.fluent.extree.expression;

import static org.junit.Assert.assertEquals;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.function.Function;

import org.junit.Test;

import co.streamx.fluent.extree.expression.Expression;
import co.streamx.fluent.extree.expression.ExpressionType;
import co.streamx.fluent.extree.expression.Interpreter;
import co.streamx.fluent.extree.expression.LambdaExpression;

public class InvocationExpressionTest {
	@Test
	public void testParameterReplacementInToString() throws NoSuchMethodException, SecurityException {

		Expression firstInvocation = Expression.invoke(Expression.constant("Hello World"),
				String.class.getMethod("replace", CharSequence.class, CharSequence.class), Expression.parameter(String.class, 0), Expression.constant("Eddie"));

		assertEquals("Hello World.replace(P0, Eddie)", firstInvocation.toString());

		Expression secondInvocation = Expression.invoke(firstInvocation, String.class.getMethod("replace", CharSequence.class, CharSequence.class),
				Expression.constant("foo"), Expression.constant("bar"));

		assertEquals("Hello World.replace(P0, Eddie).replace(foo, bar)", secondInvocation.toString());
	}

	@Test
	public void compareComparableObjects() {
		LocalDate date1 = LocalDate.of(2016, 8, 23);
		LocalDate date2 = LocalDate.now();
		Expression comparison = Expression.binary(ExpressionType.GreaterThan, Expression.constant(date1), Expression.constant(date2));

		Object actual = comparison.visit(Interpreter.Instance).apply(null);
		assertEquals(date1.isAfter(date2), actual);

		comparison = Expression.binary(ExpressionType.LessThan, Expression.constant(date1), Expression.constant(date2));

		actual = comparison.visit(Interpreter.Instance).apply(null);
		assertEquals(date1.isBefore(date2), actual);
	}

    @Test
    public void testParsedMethod() throws Exception {
        LambdaExpression<?> parsed = LambdaExpression
                .parseMethod(InvocationExpressionTest.class.getDeclaredMethod("method", Integer.TYPE, Integer.TYPE), this);
        Object result = parsed.compile().apply(new Object[] { 2, 3 });
        assertEquals(5, result);
    }

    public int method(int a,
                      int b) {
        return a + b;
    }

    static class Entity { public int id; }

	@Test
	public void testIssue2() throws Exception {
		Function<Entity, Integer> lambda = (Serializable & Function<Entity, Integer>) e -> e.id;
		SimpleExpressionVisitor visitor = new SimpleExpressionVisitor(){ };
		LambdaExpression.parse(lambda).accept(visitor);
	}
}
