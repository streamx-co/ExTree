package co.streamx.fluent.extree.expression;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 
 */

final class TypeConverter extends SimpleExpressionVisitor {
	private final Class<?> _to;

	// see http://docs.oracle.com/javase/specs/jls/se8/html/jls-5.html#jls-5.1.2
	private static final Map<Class<?>, List<Class<?>>> primitiveWides;

	static {
		Map<Class<?>, List<Class<?>>> wides = new HashMap<>();
        wides.put(Byte.class,
                Arrays.asList(new Class<?>[] { Short.class, Integer.class, Long.class, Float.class, Double.class }));
        wides.put(Short.class, Arrays.asList(new Class<?>[] { Integer.class, Long.class, Float.class, Double.class }));

        wides.put(Character.class, Arrays.asList(new Class<?>[] { String.class, CharSequence.class }));

        wides.put(Integer.class, Arrays.asList(new Class<?>[] { Long.class, Float.class, Double.class }));

        wides.put(Long.class, Arrays.asList(new Class<?>[] { Float.class, Double.class }));

        wides.put(Float.class, Arrays.asList(new Class<?>[] { Double.class }));

		primitiveWides = wides;
    }

    // https://stackoverflow.com/questions/3473756/java-convert-primitive-class/17836370
    private static final Class<?>[] wrappers = { Integer.class, Double.class, Byte.class, Boolean.class,
            Character.class, Void.class, Short.class, Float.class, Long.class };

    @SuppressWarnings("unchecked")
    private static <T> Class<T> wrap(final Class<T> clazz) {
        if (!clazz.isPrimitive())
            return clazz;
        final String name = clazz.getName();
        final int c0 = name.charAt(0);
        final int c2 = name.charAt(2);
        final int mapper = (c0 + c0 + c0 + 5) & (118 - c2);
        return (Class<T>) wrappers[mapper];
    }

	private TypeConverter(Class<?> to) {
		_to = to;
	}

	static Expression convert(Expression e, Class<?> to) {
        if (e == null)
            return Expression.block(to, Collections.emptyList());
		Class<?> from = e.getResultType();
        if (to.isAssignableFrom(from))
			return e;

		return e.accept(new TypeConverter(to));
	}

	private Object convert(Class<?> from, Object value) {

		if (from == Integer.TYPE)
			return convert((Integer) value);

		return defaultConvert(value);
	}

	private Object convert(int value) {
		if (_to == Boolean.TYPE) {

			if (value == 0)
				return Boolean.FALSE;

			if (value == 1)
				return Boolean.TRUE;
		}
        else if (_to == Character.TYPE) {
            return (char) value;
        }

		return defaultConvert(value);
	}

	private Expression defaultConvert(Expression e) {
		if (isAssignable(_to, e.getResultType()))
			return e;

		return Expression.convert(e, _to);
	}

	private Object defaultConvert(Object value) {
		return _to.cast(value);
	}

	@Override
	public Expression visit(BinaryExpression e) {
		if (isAssignable(_to, e.getResultType()))
			return e;
		Expression first = e.getFirst().accept(this);
		Expression second = e.getSecond().accept(this);
		Expression op = e.getOperator();

		return Expression.condition(op, first, second);
	}

	@Override
	public Expression visit(ConstantExpression e) {
		Class<?> resultType = e.getResultType();
		if (isAssignable(_to, resultType))
			return e;
		return Expression.constant(convert(resultType, e.getValue()), _to);
	}

	@Override
	public Expression visit(InvocationExpression e) {
		return defaultConvert(e);
	}

	@Override
	public Expression visit(LambdaExpression<?> e) {
		return defaultConvert(e);
	}

	@Override
	public Expression visit(MemberExpression e) {
		return defaultConvert(e);
	}

	@Override
	public Expression visit(ParameterExpression e) {
		if (isAssignable(e.getResultType(), _to))
			return Expression.parameter(_to, e.getIndex());
		return defaultConvert(e);
	}

	@Override
	public Expression visit(UnaryExpression e) {
		return defaultConvert(e);
	}

	public static boolean isAssignable(Class<?> to, Class<?> from) {

        to = wrap(to);
        from = wrap(from);

		if (to.isAssignableFrom(from))
			return true;

		List<Class<?>> wides = primitiveWides.get(from);
        return (wides != null) && wides.contains(to);
	}
}
