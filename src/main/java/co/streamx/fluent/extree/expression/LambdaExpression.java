package co.streamx.fluent.extree.expression;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import lombok.Getter;
import lombok.NonNull;

/**
 * Describes a lambda expression. This captures a block of code that is similar to a method body.
 * <p>
 * Use {@link #parse(Object)} method to get a lambda expression tree.
 * </p>
 * 
 * @param <F> type of the lambda represented by this LambdaExpression.
 * 
 * 
 */

@Getter
public final class LambdaExpression<F> extends InvocableExpression {

    private final Expression body;
    private final List<Expression> locals;
    private final Object key;
    private final Supplier<LambdaExpression<F>> parser;

    // private static final Map<Class<?>, WeakReference<LambdaExpression<?>>> _cache = Collections
    // .synchronizedMap(new WeakHashMap<Class<?>, WeakReference<LambdaExpression<?>>>());

    LambdaExpression(Class<?> resultType, @NonNull Expression body, List<ParameterExpression> params,
                     @NonNull List<Expression> locals, Object key, Supplier<LambdaExpression<F>> parser) {
        super(ExpressionType.Lambda, resultType, params);

        if (!TypeConverter.isAssignable(resultType, body.getResultType()))
            throw new IllegalArgumentException(body.getResultType() + " is not assignable to " + resultType);

        this.body = body;
        this.locals = locals;
        this.key = key;
        this.parser = parser;
    }

    /**
     * Gets a value indicating whether the lambda expression tree node represents a lambda expression calling a
     * method.
     */
    public boolean isMethodRef() {
        return parser != null;
    }

    /**
     * If the LambdaExpression wraps a method call, then returns the method representation as an AST.<br/>
     * Otherwise, returns the current lambda expression.<br/>
     * The result is always semantically equivalent to the current lambda expression.
     */
    public LambdaExpression<F> parseMethodRef() {
        return isMethodRef() ? parser.get() : this;
    }

    /**
     * Creates {@link LambdaExpression} representing the lambda expression tree.
     * 
     * @param        <T> the type of lambda to parse
     * 
     * @param lambda - the lambda
     * 
     * @return {@link LambdaExpression} representing the lambda expression tree.
     */
    @SuppressWarnings("unchecked")
    public static <T> LambdaExpression<T> parse(T lambda) {

        LambdaExpression<T> lambdaE;
        // WeakReference<LambdaExpression<?>> wlambda = _cache.get(lambda.getClass());
        // if (wlambda != null) {
        // lambdaE = (LambdaExpression<T>) wlambda.get();
        // if (lambdaE != null)
        // return (LambdaExpression<T>) lambdaE.accept(new InstanceReplacer(lambda));
        // }

        lambdaE = (LambdaExpression<T>) ExpressionClassCracker.get().lambda(lambda, true);

        // _cache.put(lambda.getClass(), new WeakReference<LambdaExpression<?>>(lambdaE));

        return lambdaE;
    }

    /**
     * Creates {@link LambdaExpression} representing the passed method expression tree.
     * 
     * @param <T>             the type of lambda to parse
     * 
     * @param methodReference - reference to a method, e.g. MyClass::doWork
     * 
     * @return {@link LambdaExpression} representing the lambda expression tree.
     */
    @SuppressWarnings("unchecked")
    public static <T> LambdaExpression<T> parseMethod(T methodReference) {

        return (LambdaExpression<T>) ExpressionClassCracker.get().lambda(methodReference, false);
    }

    /**
     * Creates {@link LambdaExpression} representing the passed method expression tree.
     * 
     * @param method   to parse
     * @param instance Required if the method is not static, otherwise null.
     * @return {@link LambdaExpression} representing the lambda expression tree.
     */
    public static LambdaExpression<?> parseMethod(Method method,
                                            Object instance) {
        if (Modifier.isStatic(method.getModifiers()) ^ instance == null)
            throw new IllegalArgumentException("Instance does not suit the method: " + method);
        return ExpressionClassCracker.get()
                .lambdaFromFileSystem(instance, method, method.getDeclaringClass().getClassLoader());
    }

    /**
     * Produces a {@link Function} that represents the expression.
     * 
     * @param e {@link Expression} to compile
     * @return {@link Function} that represents the expression.
     */
    public static Function<Object[], ?> compile(Expression e) {
        return e.accept(Interpreter.Instance);
    }

    /**
     * Produces a {@link Function} that represents the lambda expression.
     * 
     * @return {@link Function} that represents the lambda expression.
     */
    public Function<Object[], ?> compile() {
        final Function<Object[], ?> f = accept(Interpreter.Instance);
        return (Object[] pp) -> {
            Function<Object[], ?> f1 = (Function<Object[], ?>) f.apply(pp);
            return f1.apply(null);
        };
    }

    @Override
    protected <T> T visit(ExpressionVisitor<T> v) {
        return v.visit(this);
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append('{');
        List<ParameterExpression> arguments = getParameters();
        if (arguments.size() > 0) {
            b.append('(');
            for (int i = 0; i < arguments.size(); i++) {
                if (i > 0) {
                    b.append(',');
                    b.append(' ');
                }
                ParameterExpression pe = arguments.get(i);
                b.append(pe.getResultType().getName());
                b.append(' ');
                b.append(pe.toString());
            }
            b.append(')');
        }
        b.append(" -> ");
        final boolean hasLocals = !locals.isEmpty();
        if (hasLocals) {
            b.append("{\n");
            for (int i = 0; i < locals.size(); i++) {
                Expression e = locals.get(i);
                if (e == null)
                    continue;
                b.append("LOCAL[");
                b.append(i + arguments.size());
                b.append("] = ");
                b.append(e);
                b.append('\n');
            }
            if (getResultType() != Void.TYPE)
                b.append("return ");
        }
        b.append(getBody().toString());
        if (hasLocals)
            b.append("\n}");
        b.append('}');
        return b.toString();
    }
}
