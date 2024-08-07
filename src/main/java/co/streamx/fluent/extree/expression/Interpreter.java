package co.streamx.fluent.extree.expression;

import static co.streamx.fluent.extree.function.Functions.add;
import static co.streamx.fluent.extree.function.Functions.and;
import static co.streamx.fluent.extree.function.Functions.bitwiseAnd;
import static co.streamx.fluent.extree.function.Functions.bitwiseNot;
import static co.streamx.fluent.extree.function.Functions.bitwiseOr;
import static co.streamx.fluent.extree.function.Functions.constant;
import static co.streamx.fluent.extree.function.Functions.divide;
import static co.streamx.fluent.extree.function.Functions.equal;
import static co.streamx.fluent.extree.function.Functions.greaterThan;
import static co.streamx.fluent.extree.function.Functions.greaterThanOrEqual;
import static co.streamx.fluent.extree.function.Functions.iif;
import static co.streamx.fluent.extree.function.Functions.instanceOf;
import static co.streamx.fluent.extree.function.Functions.lessThan;
import static co.streamx.fluent.extree.function.Functions.lessThanOrEqual;
import static co.streamx.fluent.extree.function.Functions.modulo;
import static co.streamx.fluent.extree.function.Functions.multiply;
import static co.streamx.fluent.extree.function.Functions.negate;
import static co.streamx.fluent.extree.function.Functions.not;
import static co.streamx.fluent.extree.function.Functions.or;
import static co.streamx.fluent.extree.function.Functions.shiftLeft;
import static co.streamx.fluent.extree.function.Functions.shiftRight;
import static co.streamx.fluent.extree.function.Functions.subtract;
import static co.streamx.fluent.extree.function.Functions.xor;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 *
 */

final class Interpreter implements ExpressionVisitor<Function<Object[], ?>> {

    static final Interpreter Instance = new Interpreter();
    private static final Object[] emptyArray = new Object[0];

    private Interpreter() {
    }

    private Function<Object[], ?> normalize(BiFunction<Object[], Object[], ?> source) {
        return pp -> source.apply(pp, pp);
    }

    private Function<Object[], Boolean> normalize(BiPredicate<Object[], Object[]> source) {
        return pp -> source.test(pp, pp);
    }

    private Function<Object[], Boolean> normalize(Predicate<Object[]> source) {
        return pp -> source.test(pp);
    }

    // https://stackoverflow.com/questions/3473756/java-convert-primitive-class/17836370
    private static final Class<?>[] wrappers = {Integer.class, Double.class, Byte.class, Boolean.class,
            Character.class, Void.class, Short.class, Float.class, Long.class};

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

    @SuppressWarnings("unchecked")
    @Override
    public Function<Object[], ?> visit(BinaryExpression e) {
        final Function<Object[], ?> first = e.getFirst().accept(this);
        final Function<Object[], ?> second = e.getSecond().accept(this);
        switch (e.getExpressionType()) {
            case ExpressionType.Add:
                return normalize(add((Function<Object[], Number>) first, (Function<Object[], Number>) second));
            case ExpressionType.BitwiseAnd:
                return normalize(bitwiseAnd((Function<Object[], Number>) first, (Function<Object[], Number>) second));
            case ExpressionType.LogicalAnd:
                return normalize(and((Function<Object[], Boolean>) first, (Function<Object[], Boolean>) second));
            case ExpressionType.ArrayIndex:
                return t -> Array.get(first.apply(t), (Integer) second.apply(t));
            // return new Function<Object, Object[]>() {
            // // @Override
            // public Object invoke(Object[] t) throws Throwable {
            // return Array.get(first.invoke(t), (Integer) second
            // .invoke(t));
            // }
            // };
            // case ExpressionType.Coalesce:
            // return coalesce((Function<?, Object[]>) first,
            // (Function<?, Object[]>) second);
            case ExpressionType.Conditional:
                return iif((Function<Object[], Boolean>) e.getOperator().accept(this), first, second);
            case ExpressionType.Divide:
                return normalize(divide((Function<Object[], Number>) first, (Function<Object[], Number>) second));
            case ExpressionType.Equal:
                return normalize(equal(first, second));
            case ExpressionType.ExclusiveOr:
                return normalize(xor((Function<Object[], Number>) first, (Function<Object[], Number>) second));
            case ExpressionType.GreaterThan:
                return normalize(
                        greaterThan((Function<Object[], Comparable>) first, (Function<Object[], Comparable>) second));
            case ExpressionType.GreaterThanOrEqual:
                return normalize(greaterThanOrEqual((Function<Object[], Comparable>) first,
                        (Function<Object[], Comparable>) second));
            case ExpressionType.LeftShift:
                return normalize(shiftLeft((Function<Object[], Number>) first, (Function<Object[], Number>) second));
            case ExpressionType.LessThan:
                return normalize(lessThan((Function<Object[], Comparable>) first, (Function<Object[], Comparable>) second));
            case ExpressionType.LessThanOrEqual:
                return normalize(
                        lessThanOrEqual((Function<Object[], Comparable>) first, (Function<Object[], Comparable>) second));
            case ExpressionType.Modulo:
                return normalize(modulo((Function<Object[], Number>) first, (Function<Object[], Number>) second));
            case ExpressionType.Multiply:
                return normalize(multiply((Function<Object[], Number>) first, (Function<Object[], Number>) second));
            case ExpressionType.NotEqual:
                return normalize(equal(first, second).negate());
            case ExpressionType.BitwiseOr:
                return normalize(bitwiseOr((Function<Object[], Number>) first, (Function<Object[], Number>) second));
            case ExpressionType.LogicalOr:
                return normalize(or((Function<Object[], Boolean>) first, (Function<Object[], Boolean>) second));
            // case ExpressionType.Power:
            // return power((Function<Number, Object[]>) first,
            // (Function<Number, Object[]>) second);
            case ExpressionType.RightShift:
                return normalize(shiftRight((Function<Object[], Number>) first, (Function<Object[], Number>) second));
            case ExpressionType.Subtract:
                return normalize(subtract((Function<Object[], Number>) first, (Function<Object[], Number>) second));
            case ExpressionType.InstanceOf:
                return normalize(instanceOf(first, (Class<?>) second.apply(null)));
            default:
                throw new IllegalArgumentException(ExpressionType.toString(e.getExpressionType()));
        }
    }

    @Override
    public Function<Object[], ?> visit(ConstantExpression e) {
        return constant(e.getValue());
    }

    @Override
    public Function<Object[], ?> visit(NewArrayInitExpression newArrayInitExpression) {
        List<Function<Object[], ?>> args = newArrayInitExpression.getInitializers()
                .stream()
                .map(i -> (Function<Object[], ?>) i.accept(this))
                .collect(Collectors.toList());

        Class<?> componentType = newArrayInitExpression.getComponentType();

        return pp -> {

            Object r = Array.newInstance(componentType, args.size());

            for (int index = 0; index < args.size(); index++) {
                Array.set(r, index, args.get(index).apply(pp));
            }

            return r;
        };
    }

    @Override
    public Function<Object[], ?> visit(InvocationExpression e) {

        InvocableExpression target = e.getTarget();
        Function<Object[], ?> m = target.accept(this);
        Function<Object[], ?> x;
        if (target.getExpressionType() == ExpressionType.Lambda) {
            x = (Object[] pp) -> {
                Function<Object[], ?> f1 = (Function<Object[], ?>) m.apply(pp);
                return f1.apply(emptyArray);
            };
        } else {
            x = m;
        }

        int size = e.getArguments().size();
        List<Function<Object[], ?>> ppe = new ArrayList<>(size);
        for (Expression p : e.getArguments())
            ppe.add(p.accept(this));

        Function<Object[], Object[]> params = pp -> {

            if (target.getExpressionType() == ExpressionType.FieldAccess)
                return pp; // field: no arguments, just the instance

            Object[] r = new Object[ppe.size()];
            int index = 0;
            for (Function<Object[], ?> pe : ppe) {
                r[index++] = pe.apply(pp);
            }

            // for MethodAccess we need both outer and inner scope arguments
            return (target.getExpressionType() == ExpressionType.MethodAccess
                    || target.getExpressionType() == ExpressionType.Delegate) ? new Object[]{pp, r} : r;
        };

        return x.compose(params);
    }

    @Override
    public Function<Object[], ?> visit(LambdaExpression<?> e) {

        Function<Object[], ?> f = e.getBody().accept(this);

        List<Expression> locals = e.getLocals();
        int size = locals.size();
        if (size > 0) {
            List<Function<Object[], ?>> ple = new ArrayList<>(size);
            for (Expression p : locals)
                ple.add(p != null ? p.accept(this) : null);

            f = f.compose((Object[] pp) -> {
                int originalLength = pp.length;
                pp = Arrays.copyOf(pp, originalLength + size);
                for (int index = 0; index < size; index++) {
                    Function<Object[], ?> le = ple.get(index);
                    if (le == null)
                        continue;
                    pp[index + originalLength] = le.apply(pp);
                }
                return pp;
            });
        }
        return toClosure(f.compose(visitParameters(e)));
    }

    private static Function<Object[], ?> toClosure(Function<Object[], ?> f) {
        return (Function<Object[], Function<Object[], ?>>) (Object[] captured) -> (Object[] p) -> f
                .apply(concat(captured, p));
    }

    private static <T> T[] concat(T[] first,
                                  T[] second) {
        if (first == null || first.length == 0)
            return second;
        if (second == null || second.length == 0)
            return first;
        T[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    private Function<Object[], Object[]> visitParameters(InvocableExpression invocable) {
        List<ParameterExpression> parameters = invocable.getParameters();
        int size = parameters.size();
        List<Function<Object[], ?>> ppe = new ArrayList<>(size);
        for (ParameterExpression p : parameters)
            ppe.add(p.accept(this));

        Function<Object[], Object[]> params = pp -> {
            Object[] r = new Object[ppe.size()];
            int index = 0;
            for (Function<Object[], ?> pe : ppe) {
                r[parameters.get(index++).getIndex()] = pe.apply(pp);
            }
            return r;
        };

        return params;
    }

    @Override
    public Function<Object[], ?> visit(DelegateExpression e) {
        final Function<Object[], ?> f = e.getDelegate().accept(this);

        Function<Object[], Object[]> params = visitParameters(e);

        return t -> {
            InvocableExpression l = (InvocableExpression) f.apply((Object[]) t[0]);
            Function<Object[], ?> f1 = (Function<Object[], ?>) l.accept(this).apply(params.apply((Object[]) t[1]));
            return l.getExpressionType() == ExpressionType.Lambda ? f1.apply(emptyArray) : f1;
        };
    }

    @Override
    public Function<Object[], ?> visit(BlockExpression e) {

        List<Function<Object[], ?>> ff = new ArrayList<>();
        for (Expression s : e.getExpressions())
            ff.add(s.accept(this));
        
        return t -> {
            Object result = null;
            for (Function<Object[], ?> f : ff)
                result = f.apply(t);
            return result;
        };
    }

    @Override
    public Function<Object[], ?> visit(MemberExpression e) {
        final Member m = e.getMember();

        if (!Modifier.isPublic(m.getModifiers()) && m instanceof AccessibleObject) {
            AccessibleObject ao = (AccessibleObject) m;
            try {
                if (!ao.isAccessible())
                    ao.setAccessible(true);
            } catch (Exception ee) {
                // suppress
            }
        }

        Expression ei = e.getInstance();
        final Function<Object[], ?> instance = ei != null ? ei.accept(this) : null;

        Function<Object[], Object[]> params = visitParameters(e);

        Function<Object[], ?> field = t -> {
            try {
                return ((Field) m).get(instance == null ? null : instance.apply(t));
            } catch (IllegalArgumentException | IllegalAccessException ex) {
                throw new RuntimeException(ex);
            }
        };

        Function<Object[], ?> method = t -> {
            Object inst;
            if (instance != null) {
                inst = instance.apply((Object[]) t[0]);
            } else
                inst = null;
            try {
                Object[] pp = params.apply((Object[]) t[1]);
                return ((Method) m).invoke(inst, pp);
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                throw new RuntimeException(ex);
            }
        };

        Function<Object[], ?> ctor = t -> {
            try {
                return ((Constructor<?>) m).newInstance(params.apply(t));
            } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                     | InvocationTargetException ex) {
                throw new RuntimeException(ex);
            }
        };

        Function<Object[], ?> member;

        if (m instanceof Field)
            member = field;
        else if (m instanceof Method)
            member = method;
        else
            member = ctor;

        return member;// .compose(params);
    }

    @Override
    public Function<Object[], ?> visit(ParameterExpression e) {
        final int index = e.getIndex();
        return t -> t[index];
    }

    @SuppressWarnings("unchecked")
    @Override
    public Function<Object[], ?> visit(UnaryExpression e) {
        final Function<Object[], ?> first = e.getFirst().accept(this);
        switch (e.getExpressionType()) {
            case ExpressionType.ArrayLength:
                return t -> Array.getLength(first.apply(t));
            case ExpressionType.BitwiseNot:
                return (Function<Object[], ?>) bitwiseNot((Function<Object[], Number>) first);
            case ExpressionType.Convert:
                final Class<?> to = e.getResultType();
                if (to.isPrimitive() || Number.class.isAssignableFrom(to))
                    return t -> {
                        Object source = first.apply(t);
                        if (source instanceof Number) {
                            Number result = (Number) source;
                            if (to.isPrimitive()) {
                                if (to == Integer.TYPE)
                                    return result.intValue();
                                if (to == Long.TYPE)
                                    return result.longValue();
                                if (to == Float.TYPE)
                                    return result.floatValue();
                                if (to == Double.TYPE)
                                    return result.doubleValue();
                                if (to == Byte.TYPE)
                                    return result.byteValue();
                                if (to == Character.TYPE)
                                    return (char) result.intValue();
                                if (to == Short.TYPE)
                                    return result.shortValue();
                            } else if (result != null) {
                                if (to == BigInteger.class)
                                    return BigInteger.valueOf(result.longValue());
                                if (to == BigDecimal.class)
                                    return BigDecimal.valueOf(result.doubleValue());
                            }
                        }
                        if (source instanceof Character) {
                            if (to == Character.TYPE)
                                return (char) source;
                            if (to == Integer.TYPE)
                                return (int) (char) source;
                            if (to == Long.TYPE)
                                return (long) (char) source;
                            if (to == Float.TYPE)
                                return (float) (char) source;
                            if (to == Double.TYPE)
                                return (double) (char) source;
                        }
                        return wrap(to).cast(source);
                    };

                return first;
            case ExpressionType.IsNull:
                return first.andThen(r -> r == null);
            case ExpressionType.IsNonNull:
                return first.andThen(r -> r != null);
            case ExpressionType.LogicalNot:
                return normalize(not((Function<Object[], Boolean>) first));
            case ExpressionType.Negate:
                return (Function<Object[], ?>) negate((Function<Object[], Number>) first);
            // case ExpressionType.UnaryPlus:
            // return abs((Function<? extends Number, Object[]>) first);
            default:
                throw new IllegalArgumentException(ExpressionType.toString(e.getExpressionType()));
        }
    }
}
